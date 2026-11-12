package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import com.github.tomakehurst.wiremock.client.WireMock
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import no.nav.security.token.support.spring.SpringTokenValidationContextHolder
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.security.token.support.spring.validation.interceptor.BearerTokenClientHttpRequestInterceptor
import no.nav.sifinnsynapi.http.MDCValuesPropagatingClientHttpRequestInterceptor
import no.nav.sifinnsynapi.util.Constants
import no.nav.sifinnsynapi.util.personIdent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock
import java.time.Duration
import java.util.*


@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.allow-bean-definition-overriding=true"]
)
@EnableWireMock(ConfigureWireMock())
@ActiveProfiles("test")
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test. Se application-test.yml -> no.nav.security.jwt.issuer.selvbetjening for konfigurasjon
class LegacyInnsynApiServiceTest {

    @Autowired
    lateinit var legacyInnsynApiService: LegacyInnsynApiService

    @Autowired
    lateinit var legacySøknadRepository: LegacySøknadRepository

    companion object {
        const val sifInnsynApiMockUrl = "sif-innsyn-api-base-url-mock"
        const val innloggetFnr = "01015450572"
        const val annenFnr = "01010000382"
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
                .connectTimeout(Duration.ofSeconds(20))
                .readTimeout(Duration.ofSeconds(20))
                .defaultHeader(Constants.X_CORRELATION_ID, UUID.randomUUID().toString())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, ArgumentMatchers.anyString())
                .defaultHeader("X-K9-Ytelse", "PLEIEPENGER_SYKT_BARN")
                .baseUri(sifInnsynApiBaseUrl)
                .build()
        }
    }

    @BeforeEach
    fun setUp() {
        legacySøknadRepository.deleteAll()
        mockkStatic("no.nav.sifinnsynapi.util.TokenUtilsKt")
        every { any<SpringTokenValidationContextHolder>().personIdent() } returns innloggetFnr
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("no.nav.sifinnsynapi.util.TokenUtilsKt")
    }

    @Test
    fun `Gitt cachet søknad som tilhører innlogget bruker, forvent at den returneres uten restkall`() {
        val søknadId = UUID.randomUUID()
        legacySøknadRepository.save(legacySøknadDAO(søknadId, innloggetFnr))

        val søknad = legacyInnsynApiService.hentLegacySøknad(søknadId.toString())

        assertEquals(søknadId, søknad.søknadId)
        WireMock.verify(0, WireMock.getRequestedFor(WireMock.urlEqualTo("/$sifInnsynApiMockUrl/soknad/$søknadId")))
    }

    @Test
    fun `Gitt cachet søknad som tilhører annen bruker, forvent LegacySøknadNotFoundException`() {
        val søknadId = UUID.randomUUID()
        legacySøknadRepository.save(legacySøknadDAO(søknadId, annenFnr))
        stubSøknad(søknadId, annenFnr)

        assertThrows<LegacySøknadNotFoundException> { legacyInnsynApiService.hentLegacySøknad(søknadId.toString()) }
        WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/$sifInnsynApiMockUrl/soknad/$søknadId")))
    }

    @Test
    fun `Gitt søknad fra sif-innsyn-api som tilhører annen bruker, forvent LegacySøknadNotFoundException og ingen lagring`() {
        val søknadId = UUID.randomUUID()
        stubSøknad(søknadId, annenFnr)

        assertThrows<LegacySøknadNotFoundException> { legacyInnsynApiService.hentLegacySøknad(søknadId.toString()) }
        assertFalse(legacySøknadRepository.existsById(søknadId.toString()))
        WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/$sifInnsynApiMockUrl/soknad/$søknadId")))
    }

    @Test
    fun `Gitt søknad fra sif-innsyn-api som tilhører innlogget bruker, forvent at den returneres og lagres`() {
        val søknadId = UUID.randomUUID()
        stubSøknad(søknadId, innloggetFnr)

        val søknad = legacyInnsynApiService.hentLegacySøknad(søknadId.toString())

        assertEquals(søknadId, søknad.søknadId)
        assertNotNull(legacySøknadRepository.finnForSøknadIdOgFødselsnummer(søknadId.toString(), innloggetFnr))
    }

    @Test
    fun `Gitt flere cachede søknader, forvent at kun innlogget brukers søknader returneres`() {
        val egenSøknadId = UUID.randomUUID()
        val fremmedSøknadId = UUID.randomUUID()
        legacySøknadRepository.save(legacySøknadDAO(egenSøknadId, innloggetFnr))
        legacySøknadRepository.save(legacySøknadDAO(fremmedSøknadId, annenFnr))
        stubSøknad(fremmedSøknadId, annenFnr)

        val søknader = legacyInnsynApiService.hentLegacySøknader(listOf(egenSøknadId.toString(), fremmedSøknadId.toString()))

        assertEquals(listOf(egenSøknadId), søknader.map { it.søknadId })
    }

    private fun søknadJson(fnr: String) =
        //language=json
        """{"søker": {"fødselsnummer": "$fnr", "fornavn": "Ola", "etternavn": "Nordmann"}}"""

    private fun legacySøknadDAO(søknadId: UUID, fnr: String) = LegacySøknadDAO(
        søknadId = søknadId.toString(),
        søknadstype = LegacySøknadstype.PP_SYKT_BARN.name,
        søknad = søknadJson(fnr),
        saksId = null,
        journalpostId = "123456789"
    )

    private fun stubSøknad(søknadId: UUID, fnr: String) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/$sifInnsynApiMockUrl/soknad/$søknadId"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            //language=json
                            """
                            {
                              "søknadId": "$søknadId",
                              "søknadstype": "PP_SYKT_BARN",
                              "søknad": ${søknadJson(fnr)},
                              "saksId": null,
                              "journalpostId": "123456789"
                            }
                            """.trimIndent()
                        )
                )
        )
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

        WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/$sifInnsynApiMockUrl/soknad/$søknadId")))
    }
}
