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
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
class LegacyInnsynApiClientConfig(
    @Value("\${no.nav.gateways.sif-innsyn-api-base-url}") private val sifInnsynApiBaseUrl: String,
    oauth2Config: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService
) {

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
}
