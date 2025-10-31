package no.nav.sifinnsynapi.enhetsregisteret

import no.nav.sifinnsynapi.http.MDCValuesPropagatingClientHttpRequestInterceptor
import no.nav.sifinnsynapi.util.RestTemplateUtils.requestLoggerInterceptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import java.time.Duration

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
        return builder
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(20))
            .defaultHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .rootUri(enhetsregisterBaseUrl)
            .defaultMessageConverters()
            .interceptors(mdcInterceptor, requestLoggerInterceptor(logger))
            .build()
    }
}

