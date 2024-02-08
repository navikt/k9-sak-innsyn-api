package no.nav.sifinnsynapi.dokumentoversikt

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.sifinnsynapi.util.Constants.NAV_CALL_ID
import no.nav.sifinnsynapi.util.Constants.X_CORRELATION_ID
import no.nav.sifinnsynapi.util.MDCUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.AUTHORIZATION
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClientRequest
import reactor.netty.http.client.HttpClientResponse
import java.util.*

@Configuration
class SafSelvbetjeningClientsConfig(
    oauth2Config: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService,
    @Value("\${no.nav.gateways.saf-selvbetjening-base-url}") private val safSelvbetjeningBaseUrl: String
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(SafSelvbetjeningClientsConfig::class.java)
    }

    private val tokenxSafSelvbetjeningClientProperties = oauth2Config.registration["tokenx-safselvbetjening"]
        ?: throw RuntimeException("could not find oauth2 client config for tokenx-safselvbetjening")

    @Bean("safSelvbetjeningGraphQLClient")
    fun graphQLClient() = GraphQLWebClient(
        url = "${safSelvbetjeningBaseUrl}/graphql",
        builder = WebClient.builder()
            .clientConnector(
                ReactorClientHttpConnector(
                    HttpClient.create()
                        .doOnRequest { request: HttpClientRequest, _ ->
                            logger.info("{} {} {}", request.version(), request.method(), request.resourceUrl())
                        }
                        .doOnResponse { response: HttpClientResponse, _ ->
                            logger.info("{} - {} {} {}", response.status().toString(), response.version(), response.method(), response.resourceUrl())
                        }
                )
            )
            .defaultRequest {
                val correlationId = MDCUtil.callIdOrNew()
                it.header(NAV_CALL_ID, correlationId)
                it.header(X_CORRELATION_ID, correlationId)
                it.header(
                    AUTHORIZATION,
                    "Bearer ${oAuth2AccessTokenService.getAccessToken(tokenxSafSelvbetjeningClientProperties).accessToken}"
                )
            }
    )


    @Bean
    fun safSelvbetjeningRestTemplate(
        restTemplateBuilder: RestTemplateBuilder,
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): RestTemplate {

        logger.info("Konfigurerer opp tokenx klient for safselvbetjening.")
        return restTemplateBuilder
            .rootUri(safSelvbetjeningBaseUrl)
            .defaultHeader(NAV_CALL_ID, UUID.randomUUID().toString())
            .additionalInterceptors(bearerTokenInterceptor(tokenxSafSelvbetjeningClientProperties, oAuth2AccessTokenService), requestLoggerInterceptor())
            .build()
    }

    private fun bearerTokenInterceptor(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
            request.headers.setBearerAuth(response.accessToken)
            execution.execute(request, body)
        }
    }

    private fun requestLoggerInterceptor() =
        ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            logger.info("{} {}", request.method, request.uri)
            execution.execute(request, body)
        }
}
