package no.nav.sifinnsynapi.sikkerhet.abac

import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.sifinnsynapi.http.MDCValuesPropagatingClientHttpRequestInterceptor
import no.nav.sifinnsynapi.util.RestTemplateUtils.requestLoggerInterceptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
class SifAbacPdpKlientKonfig(
    @Value("\${no.nav.gateways.sif-abac-pdp}") private val sifAbacPdpUrl: String,
    oauth2Config: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService,
) {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(SifAbacPdpKlientKonfig::class.java)
        const val AZURE_SIF_ABAC_PDP = "azure-sif-abac-pdp"
    }

    private val azureSifAbacPdpClientProperties =
        oauth2Config.registration[AZURE_SIF_ABAC_PDP]
            ?: throw RuntimeException("could not find oauth2 client config for $AZURE_SIF_ABAC_PDP")

    @Bean(name = ["sifAbacPdpKlient"])
    fun restTemplate(
        builder: RestTemplateBuilder,
        mdcInterceptor: MDCValuesPropagatingClientHttpRequestInterceptor,
    ): RestTemplate = builder
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(5))
        .defaultHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .rootUri(sifAbacPdpUrl)
        .interceptors(bearerTokenInterceptor(), mdcInterceptor, requestLoggerInterceptor(logger))
        .build()

    // OBO fra saksbehandlerens token, ingen fallback til systemtoken.
    private fun bearerTokenInterceptor() = ClientHttpRequestInterceptor { request, body, execution ->
        val token = oAuth2AccessTokenService.getAccessToken(azureSifAbacPdpClientProperties).access_token
            ?: throw SecurityException("Access token er null")
        request.headers.setBearerAuth(token)
        execution.execute(request, body)
    }
}
