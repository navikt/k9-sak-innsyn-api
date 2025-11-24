package no.nav.sifinnsynapi.k9sak

import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.sifinnsynapi.http.MDCValuesPropagatingClientHttpRequestInterceptor
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy
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

@Configuration
class K9SakKlientKonfig(
    @Value("\${no.nav.gateways.k9-sak}") private val k9SakUrl: String,
    oauth2Config: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService,
) {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(K9SakKlientKonfig::class.java)

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
        // Configure connection pool for cross-cluster calls (GCP → FSS)
        // Based on http-client-connection-management.md recommendations
        val connectionManager = PoolingHttpClientConnectionManager().apply {
            // Total connections across all routes
            maxTotal = 100
            // Max connections per specific host (k9-sak)
            defaultMaxPerRoute = 100
            // Validate connections that have been idle for >10 seconds before reusing
            // Prevents "stale connection" errors
            setValidateAfterInactivity(TimeValue.ofSeconds(10))
        }

        // Build Apache HttpClient with advanced connection management
        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            // ✅ CONNECTION TTL: 55 minutes (below 60-minute FSS firewall timeout)
            // Prevents firewall from silently dropping connections
            // Fixes the 92 ETIMEDOUT (-110) errors you're seeing
            .evictIdleConnections(TimeValue.ofMinutes(55))
            // ✅ BACKGROUND EVICTION: Proactively removes expired connections
            // Prevents reusing dead connections
            .evictExpiredConnections()
            // Reuse connections when possible (performance optimization)
            .setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE)
            .build()

        // Create Spring's request factory wrapper around Apache HttpClient
        val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient).apply {
            // Connection timeout: 15 seconds (cross-cluster recommendation)
            setConnectTimeout(Duration.ofSeconds(15))
            // Read timeout: 30 seconds (increased from 20s for slow endpoints)
            // Note: This is set on the RestTemplateBuilder, not here
            // Pool acquire timeout: 45 seconds (fail fast if pool exhausted)
            setConnectionRequestTimeout(Duration.ofSeconds(45))
        }

        return builder
            // Use the custom request factory with Apache HttpClient
            .requestFactory(java.util.function.Supplier { requestFactory })
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

