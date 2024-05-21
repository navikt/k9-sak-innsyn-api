package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.security.token.support.spring.validation.interceptor.BearerTokenClientHttpRequestInterceptor
import no.nav.sifinnsynapi.http.MDCValuesPropagatingClientHttpRequestInterceptor
import no.nav.sifinnsynapi.util.Constants
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.util.*


@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.allow-bean-definition-overriding=true"]
)
@AutoConfigureWireMock
@ActiveProfiles("test")
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test. Se application-test.yml -> no.nav.security.jwt.issuer.selvbetjening for konfigurasjon
class LegacyInnsynApiServiceTest {

    @Autowired
    lateinit var legacyInnsynApiService: LegacyInnsynApiService

    @Autowired
    lateinit var wireMockServer: WireMockServer

    companion object {
        const val sifInnsynApiMockUrl = "sif-innsyn-api-base-url-mock"
    }

    @TestConfiguration
    class TestConfig {

        @Value("\${no.nav.gateways.sif-innsyn-api-base-url}")
        private lateinit var sifInnsynApiBaseUrl: String

        @Bean(name = ["sifInnsynApiClient"])
        @Primary
        fun restTemplate(
            builder: RestTemplateBuilder,
            tokenInterceptor: BearerTokenClientHttpRequestInterceptor,
            mdcInterceptor: MDCValuesPropagatingClientHttpRequestInterceptor,
        ): RestTemplate {
            return builder
                .setConnectTimeout(Duration.ofSeconds(20))
                .setReadTimeout(Duration.ofSeconds(20))
                .defaultHeader(Constants.X_CORRELATION_ID, UUID.randomUUID().toString())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, ArgumentMatchers.anyString())
                .defaultHeader("X-K9-Ytelse", "PLEIEPENGER_SYKT_BARN")
                .defaultMessageConverters()
                .rootUri(sifInnsynApiBaseUrl)
                .build()
        }
    }

    @Test
    fun `Gitt legacy søknad ikke blir funnet, forvent at det ikke kjøres retry`() {
        val søknadId = UUID.randomUUID()

        WireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/$sifInnsynApiMockUrl/soknad/$søknadId"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(404)
                )
        )

        kotlin.runCatching { legacyInnsynApiService.hentLegacySøknad(søknadId.toString()) }

        wireMockServer.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/$sifInnsynApiMockUrl/soknad/$søknadId")))
    }
}
