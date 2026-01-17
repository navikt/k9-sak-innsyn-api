package no.nav.sifinnsynapi.enhetsregisteret

import no.nav.sifinnsynapi.http.MDCValuesPropagatingClientHttpRequestInterceptor
import no.nav.sifinnsynapi.util.RestTemplateUtils.requestLoggerInterceptor
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.util.function.Supplier

@Configuration
class EnhetsregisterKlientKonfig(
    @Value("\${no.nav.gateways.enhetsregister-base-url}") private val enhetsregisterBaseUrl: String,
) {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(EnhetsregisterKlientKonfig::class.java)
    }


    @Bean(name = ["enhetsregisterKlient"])
    fun restTemplate(
        builder: RestTemplateBuilder,
        mdcInterceptor: MDCValuesPropagatingClientHttpRequestInterceptor,
    ): RestTemplate {

        val connectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(10))              // Connection timeout (external services recommendation)
            .build()

        val connectionManager = PoolingHttpClientConnectionManager().apply {
            maxTotal = 50                                                   // Moderate pool for external service
            defaultMaxPerRoute = 50
            setValidateAfterInactivity(TimeValue.ofSeconds(10))     // Validate idle connections before reuse
            setDefaultConnectionConfig(connectionConfig)
        }

        val requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(45))
            .setResponseTimeout(Timeout.ofSeconds(20))             // Read timeout
            .build()

        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .evictIdleConnections(TimeValue.ofMinutes(5))   // Short TTL for external services
            .evictExpiredConnections()
            .build()

        val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)

        return builder
            .requestFactory(Supplier { requestFactory })
            .defaultHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .rootUri(enhetsregisterBaseUrl)
            .defaultMessageConverters()
            .interceptors(mdcInterceptor, requestLoggerInterceptor(logger))
            .build()
    }
}

