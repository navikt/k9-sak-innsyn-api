package no.nav.sifinnsynapi.k9sak

import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.sifinnsynapi.http.MDCValuesPropagatingClientHttpRequestInterceptor
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.apache.hc.core5.util.TimeValue
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
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.util.function.Supplier

@Configuration
class K9SakKlientKonfig(
    @Value("\${no.nav.gateways.k9-sak}") private val k9SakUrl: String,
    oauth2Config: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService,
) {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(K9SakKlientKonfig::class.java)
        const val TOKENX_K9_SAK = "tokenx-k9-sak"
    }

    private val tokenXK9SakClientProperties =
        oauth2Config.registration[TOKENX_K9_SAK]
            ?: throw RuntimeException("could not find oauth2 client config for $TOKENX_K9_SAK")

    @Bean(name = ["k9SakKlient"])
    fun restTemplate(
        builder: RestTemplateBuilder,
        mdcInterceptor: MDCValuesPropagatingClientHttpRequestInterceptor,
    ): RestTemplate {
        val connectionManager = PoolingHttpClientConnectionManager().apply {
            maxTotal = 100                                                   // Higher pool for slow endpoints
            defaultMaxPerRoute = 100
            setValidateAfterInactivity(TimeValue.ofSeconds(10))    // Validate idle connections before reuse
        }

        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .evictIdleConnections(TimeValue.ofMinutes(55))   // Below 60-minute FSS firewall timeout. Prevents firewall from silently dropping connections
            .evictExpiredConnections()                                              // Prevents reusing dead connections
            .build()

        val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient).apply {
            setConnectTimeout(Duration.ofSeconds(15))            // Connection timeout: 15 seconds (cross-cluster recommendation)
            setConnectionRequestTimeout(Duration.ofSeconds(45))
            setReadTimeout(Duration.ofSeconds(60))
        }

        return builder
            .requestFactory(Supplier { requestFactory })
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
                    oAuth2AccessTokenService.getAccessToken(tokenXK9SakClientProperties).access_token?.let {
                        request.headers.setBearerAuth(it)
                    }?: throw SecurityException("Access token er null")
                }
            }
            execution.execute(request, body)
        }
    }

}

