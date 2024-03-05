package no.nav.sifinnsynapi

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication(
    exclude = [
        ErrorMvcAutoConfiguration::class
    ]
)
@EnableRetry
@EnableKafka
@EnableTransactionManagement
@EnableScheduling
@ConfigurationPropertiesScan("no.nav.sifinnsynapi")
@EnableConfigurationProperties
class SifInnsynApiApplication(
    @Value("\${spring.rest.retry.maxAttempts}") private val maxAttempts: Int,
) : RetryListener {

    fun main(args: Array<String>) {
        runApplication<SifInnsynApiApplication>(*args)
    }


    private companion object {
        private val logger = LoggerFactory.getLogger(SifInnsynApiApplication::class.java)
    }

    override fun <T : Any, E : Throwable> open(context: RetryContext, callback: RetryCallback<T, E>): Boolean {
        if (context.retryCount > 0) logger.warn("Feiler ved utgående rest-kall, kjører retry")
        return true
    }

    override fun <T : Any, E : Throwable?> close(
        context: RetryContext,
        callback: RetryCallback<T, E>,
        throwable: Throwable?,
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
        throwable: Throwable,
    ) {
        val currentTry = context.retryCount
        val contextString = context.getAttribute("context.name") as String
        val backoff = context.getAttribute("backOffContext")!!
        val nextInterval = backoff.nextInterval()

        logger.warn("Forsøk {} av {}, {}", currentTry, maxAttempts, contextString.split(" ")[2])

        if (currentTry < maxAttempts) logger.info("Forsøker om: {} ms", nextInterval)
    }

    private fun Any.nextInterval(): Long {
        val getInterval = javaClass.getMethod("getInterval")
        getInterval.trySetAccessible()

        return getInterval.invoke(this) as Long
    }
}

