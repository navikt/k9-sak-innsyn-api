package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.sifinnsynapi.http.MDCValuesPropagatingClienHttpRequesInterceptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
class LegacyInnsynApiClientConfig(
    @Value("\${no.nav.gateways.sif-innsyn-api-base-url}") private val sifInnsynApiBaseUrl: String,
    @Value("\${spring.rest.retry.maxAttempts}") private val maxAttempts: Int,
    oauth2Config: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService
): RetryListener {

    private companion object {
        private val logger = LoggerFactory.getLogger(LegacyInnsynApiClientConfig::class.java)
        const val TOKEN_X_SIF_INNSYN_API = "tokenx-sif-innsyn-api"
    }

    private val tokenxSifInnsynApiClientProperties =
        oauth2Config.registration[TOKEN_X_SIF_INNSYN_API]
            ?: throw RuntimeException("could not find oauth2 client config for $TOKEN_X_SIF_INNSYN_API")

    @Bean(name = ["sifInnsynApiClient"])
    fun restTemplate(
        builder: RestTemplateBuilder,
        mdcInterceptor: MDCValuesPropagatingClienHttpRequesInterceptor
    ): RestTemplate {
        return builder
            .setConnectTimeout(Duration.ofSeconds(20))
            .setReadTimeout(Duration.ofSeconds(20))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .rootUri(sifInnsynApiBaseUrl)
            .defaultMessageConverters()
            .interceptors(bearerTokenInterceptor(), mdcInterceptor, requestLoggerInterceptor())
            .build()
    }

    override fun <T : Any, E : Throwable> open(context: RetryContext, callback: RetryCallback<T, E>): Boolean {
        if (context.retryCount > 0) logger.warn("Feiler ved utgående rest-kall, kjører retry")
        return true
    }

    override fun <T : Any, E : Throwable?> close(
        context: RetryContext,
        callback: RetryCallback<T, E>,
        throwable: Throwable?
    ) {
        val backoff = context.getAttribute("backOffContext")!!

        if (context.retryCount > 0) logger.info(
            "Gir opp etter {} av {} forsøk og {} ms",
            context.retryCount,
            maxAttempts,
            backoff.nextInterval() - 1000
        )
    }

    override fun <T : Any, E : Throwable> onError(
        context: RetryContext,
        callback: RetryCallback<T, E>,
        throwable: Throwable
    ) {
        val currentTry = context.retryCount
        val contextString = context.getAttribute("context.name") as String
        val backoff = context.getAttribute("backOffContext")!!
        val nextInterval = backoff.nextInterval()

        logger.warn("Forsøk {} av {}, {}", currentTry, maxAttempts, contextString.split(" ")[2])

        if (currentTry < maxAttempts) logger.info("Forsøker om: {} ms", nextInterval)
    }

    private fun bearerTokenInterceptor(): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            val response = oAuth2AccessTokenService.getAccessToken(tokenxSifInnsynApiClientProperties)
            request.headers.setBearerAuth(response.accessToken)
            execution.execute(request, body)
        }
    }

    private fun requestLoggerInterceptor() =
        ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            logger.info("{} {}", request.method, request.uri)
            execution.execute(request, body)
        }

    private fun Any.nextInterval(): Long {
        val getInterval = javaClass.getMethod("getInterval")
        getInterval.trySetAccessible()

        return getInterval.invoke(this) as Long
    }
}
