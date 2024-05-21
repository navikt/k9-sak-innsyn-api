package no.nav.sifinnsynapi.k9sak

import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.sifinnsynapi.http.MDCValuesPropagatingClientHttpRequestInterceptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.HttpRequest
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
class K9SakKlientKonfig(
    @Value("\${no.nav.gateways.k9-sak}") private val k9SakUrl: String,
    oauth2Config: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService,
) {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(K9SakKlientKonfig::class.java)

        const val AZURE_K9_SAK = "azure-k9-sak"
    }

    private val azureK9SakClientProperties =
        oauth2Config.registration[AZURE_K9_SAK]
            ?: throw RuntimeException("could not find oauth2 client config for $AZURE_K9_SAK")

    @Bean(name = ["k9SakKlient"])
    fun restTemplate(
        builder: RestTemplateBuilder,
        mdcInterceptor: MDCValuesPropagatingClientHttpRequestInterceptor,
    ): RestTemplate {
        return builder
            .setConnectTimeout(Duration.ofSeconds(20))
            .setReadTimeout(Duration.ofSeconds(20))
            .defaultHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .rootUri(k9SakUrl)
            .defaultMessageConverters()
            .interceptors(bearerTokenInterceptor(), mdcInterceptor)
            .build()
    }

    private fun bearerTokenInterceptor(): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            when {
                request.uri.path == "/isalive" -> {} // ignorer


                else -> {
                    oAuth2AccessTokenService.getAccessToken(azureK9SakClientProperties).accessToken?.let {
                        request.headers.setBearerAuth(it)
                    }?: throw SecurityException("Access token er null")
                }
            }
            execution.execute(request, body)
        }
    }

}

