package no.nav.sifinnsynapi.sikkerhet.abac

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.k9.felles.sikkerhet.abac.ÅrsakIkkeTilgang
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.client.core.context.JwtBearerTokenResolver
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sif.abac.kontrakt.abac.BeskyttetRessursActionAttributt
import no.nav.sif.abac.kontrakt.abac.ResourceType
import no.nav.sif.abac.kontrakt.abac.dto.OperasjonDto
import no.nav.sif.abac.kontrakt.abac.dto.PersonerOperasjonDto
import no.nav.sif.abac.kontrakt.person.AktørId
import no.nav.sifinnsynapi.utils.hentToken
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.HttpServerErrorException
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock

/**
 * Bruker ekte klient og token-utveksling mot MockOAuth2Server. Bare kilden til brukertokenet er mocket.
 */
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@EnableWireMock(ConfigureWireMock())
@ActiveProfiles("test")
@EnableMockOAuth2Server
internal class SifAbacPdpServiceTest {

    @Autowired
    lateinit var sifAbacPdpService: SifAbacPdpService

    @Autowired
    lateinit var mockOAuth2Server: MockOAuth2Server

    @MockkBean
    lateinit var jwtBearerTokenResolver: JwtBearerTokenResolver

    private lateinit var brukertoken: String

    private companion object {
        const val PERSONER_URL = "/sif-abac-pdp-mock/tilgangskontroll/v2/k9/personer"

        val forespørsel = PersonerOperasjonDto(
            listOf(AktørId("1111111111111"), AktørId("2222222222222")),
            emptyList(),
            OperasjonDto(ResourceType.FAGSAK, BeskyttetRessursActionAttributt.READ, emptySet()),
        )
    }

    @BeforeEach
    fun setUp() {
        WireMock.resetAllRequests()
        // Unikt subject per test, så token-cachen i token-support ikke deles mellom testene.
        brukertoken = mockOAuth2Server.hentToken(subject = "Z${System.nanoTime()}").serialize()
        every { jwtBearerTokenResolver.token() } returns brukertoken
    }

    @Test
    fun `innvilget tilgang sendes med utvekslet token og forespørselen følger kontrakten`() {
        stubPdp(200, """{ "harTilgang": true, "årsakerForIkkeTilgang": [] }""")

        val beslutning = sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(forespørsel)

        assertThat(beslutning.harTilgang()).isTrue()

        val request = WireMock.findAll(postRequestedFor(urlEqualTo(PERSONER_URL))).single()
        val sendtToken = request.getHeader(HttpHeaders.AUTHORIZATION).removePrefix("Bearer ")
        assertThat(sendtToken).isNotEmpty()
        assertThat(sendtToken).isNotEqualTo(brukertoken)

        WireMock.verify(
            postRequestedFor(urlEqualTo(PERSONER_URL)).withRequestBody(
                equalToJson(
                    // language=JSON
                    """
                    {
                      "aktørIder": ["1111111111111", "2222222222222"],
                      "personIdenter": [],
                      "operasjon": { "resource": "FAGSAK", "action": "READ", "aksjonspunktTyper": [] }
                    }
                    """.trimIndent()
                )
            )
        )
    }

    @Test
    fun `avslag med årsak deserialiseres`() {
        stubPdp(200, """{ "harTilgang": false, "årsakerForIkkeTilgang": ["HAR_IKKE_TILGANG_TIL_KODE6_PERSON"] }""")

        val beslutning = sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(forespørsel)

        assertThat(beslutning.harTilgang()).isFalse()
        assertThat(beslutning.årsakerForIkkeTilgang().toList())
            .containsExactly(ÅrsakIkkeTilgang.HAR_IKKE_TILGANG_TIL_KODE6_PERSON)
    }

    @Test
    fun `uten brukertoken kalles ikke sif-abac-pdp`() {
        every { jwtBearerTokenResolver.token() } returns null
        stubPdp(200, """{ "harTilgang": true, "årsakerForIkkeTilgang": [] }""")

        assertThrows<Exception> { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(forespørsel) }

        WireMock.verify(0, postRequestedFor(urlEqualTo(PERSONER_URL)))
    }

    @Test
    fun `serverfeil kastes videre etter retry`() {
        stubPdp(500, """{ "feil": "nede" }""")

        assertThrows<HttpServerErrorException> {
            sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(forespørsel)
        }

        WireMock.verify(3, postRequestedFor(urlEqualTo(PERSONER_URL)))
    }

    private fun stubPdp(status: Int, body: String) {
        WireMock.stubFor(
            WireMock.post(urlEqualTo(PERSONER_URL)).willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(body)
            )
        )
    }
}
