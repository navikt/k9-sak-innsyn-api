package no.nav.sifinnsynapi.oppslag

import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.sifinnsynapi.http.MDCValuesPropagatingClientHttpRequestInterceptor
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
import org.springframework.http.HttpRequest
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.util.function.Supplier

@Configuration
class OppslagsKlientKonfig(
    @Value("\${no.nav.gateways.k9-selvbetjening-oppslag}") private val oppslagsUrl: String,
    oauth2Config: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService,
) {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(OppslagsKlientKonfig::class.java)

        const val TOKEN_X_K9_SELVBETJENING_OPPSLAG = "tokenx-k9-selvbetjening-oppslag"
        const val AZURE_K9_SELVBETJENING_OPPSLAG = "azure-k9-selvbetjening-oppslag"
    }

    private val tokenxK9SelvbetjeningOppslagClientProperties =
        oauth2Config.registration[TOKEN_X_K9_SELVBETJENING_OPPSLAG]
            ?: throw RuntimeException("could not find oauth2 client config for $TOKEN_X_K9_SELVBETJENING_OPPSLAG")

    private val azureK9SelvbetjeningOppslagClientProperties =
        oauth2Config.registration[AZURE_K9_SELVBETJENING_OPPSLAG]
            ?: throw RuntimeException("could not find oauth2 client config for $AZURE_K9_SELVBETJENING_OPPSLAG")

    @Bean(name = ["k9OppslagsKlient"])
    fun restTemplate(
        builder: RestTemplateBuilder,
        mdcInterceptor: MDCValuesPropagatingClientHttpRequestInterceptor,
    ): RestTemplate {

        val connectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(10))              // Connection timeout (same-cluster recommendation)
            .build()

        val connectionManager = PoolingHttpClientConnectionManager().apply {
            maxTotal = 50                                                   // Moderate pool for internal service
            defaultMaxPerRoute = 50
            setValidateAfterInactivity(TimeValue.ofSeconds(10))    // Validate idle connections before reuse
            setDefaultConnectionConfig(connectionConfig)

        }

        val requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(45))
            .setResponseTimeout(Timeout.ofSeconds(20))             // Read timeout
            .build()

        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .evictIdleConnections(TimeValue.ofMinutes(10))         // Connection TTL: 10 min for same-cluster
            .evictExpiredConnections()
            .setDefaultRequestConfig(requestConfig)
            .build()

        val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)

        return builder
            .requestFactory(Supplier { requestFactory })
            .defaultHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("X-K9-Ytelse", "PLEIEPENGER_SYKT_BARN")
            .rootUri(oppslagsUrl)
            .defaultMessageConverters()
            .interceptors(bearerTokenInterceptor(), mdcInterceptor)
            .build()
    }

    private fun bearerTokenInterceptor(): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            when {
                request.uri.path == "/isalive" -> {} // ignorer
                request.uri.path.contains("/system") -> {
                    oAuth2AccessTokenService.getAccessToken(azureK9SelvbetjeningOppslagClientProperties).access_token?.let {
                        request.headers.setBearerAuth(it)
                    }?: throw SecurityException("Accesstoken er null")
                }

                else -> {
                    oAuth2AccessTokenService.getAccessToken(tokenxK9SelvbetjeningOppslagClientProperties).access_token?.let {
                        request.headers.setBearerAuth(it)
                    }?: throw SecurityException("Accesstoken er null")
                }
            }
            execution.execute(request, body)
        }
    }

}

