package no.nav.sifinnsynapi.oppslag

import no.nav.security.token.support.spring.validation.interceptor.BearerTokenClientHttpRequestInterceptor
import no.nav.sifinnsynapi.http.MDCValuesPropagatingClienHttpRequesInterceptor
import no.nav.sifinnsynapi.util.Constants.X_CORRELATION_ID
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.util.*

@Configuration
class OppslagsKlientKonfig(
        @Value("\${no.nav.gateways.k9-selvbetjening-oppslag}")
        private val oppslagsUrl: String,
        @Value("\${spring.rest.retry.maxAttempts}")
        private val maxAttempts: Int
) : RetryListener {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(OppslagsKlientKonfig::class.java)
    }

    @Bean(name = ["k9OppslagsKlient"])
    fun restTemplate(builder: RestTemplateBuilder, tokenInterceptor: BearerTokenClientHttpRequestInterceptor, mdcInterceptor: MDCValuesPropagatingClienHttpRequesInterceptor): RestTemplate {
        return builder
                .setConnectTimeout(Duration.ofSeconds(20))
                .setReadTimeout(Duration.ofSeconds(20))
                .defaultHeader(X_CORRELATION_ID, UUID.randomUUID().toString())
                .defaultHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .rootUri(oppslagsUrl)
                .defaultMessageConverters()
                .interceptors(tokenInterceptor, mdcInterceptor)
                .build()
    }

    override fun <T : Any, E : Throwable> open(context: RetryContext, callback: RetryCallback<T, E>): Boolean {
        if (context.retryCount > 0) logger.warn("Feiler ved utgående rest-kall, kjører retry")
        return true
    }

    override fun <T : Any, E : Throwable?> close(context: RetryContext, callback: RetryCallback<T, E>, throwable: Throwable?) {
        val backoff = context.getAttribute("backOffContext")!!

        if (context.retryCount > 0) logger.info("Gir opp etter {} av {} forsøk og {} ms", context.retryCount, maxAttempts, backoff.nextInterval() - 1000)
    }

    override fun <T : Any, E : Throwable> onError(context: RetryContext, callback: RetryCallback<T, E>, throwable: Throwable) {
        val currentTry = context.retryCount
        val contextString = context.getAttribute("context.name") as String
        val backoff = context.getAttribute("backOffContext")!!
        val nextInterval = backoff.nextInterval()

        logger.warn("Forsøk {} av {}, {}", currentTry, maxAttempts, contextString.split(" ")[2])

        if (currentTry < maxAttempts) logger.info("Forsøker om: {} ms", nextInterval)
    }
}

private fun Any.nextInterval(): Long {
    val getInterval = javaClass.getMethod("getInterval")
    getInterval.trySetAccessible()

    return getInterval.invoke(this) as Long
}


