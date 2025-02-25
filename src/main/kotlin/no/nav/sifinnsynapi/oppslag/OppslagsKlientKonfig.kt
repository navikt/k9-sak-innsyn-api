package no.nav.sifinnsynapi.oppslag

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
        return builder
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(20))
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

