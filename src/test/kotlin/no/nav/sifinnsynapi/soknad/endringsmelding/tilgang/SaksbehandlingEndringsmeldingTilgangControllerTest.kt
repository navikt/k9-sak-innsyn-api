package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import no.nav.k9.felles.sikkerhet.abac.ÅrsakIkkeTilgang
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.Routes.SAKSBEHANDLING_PSB_ENDRINGSMELDING_TILGANG
import no.nav.sifinnsynapi.audit.Auditlogger
import no.nav.sifinnsynapi.config.Issuers
import no.nav.sifinnsynapi.config.SecurityConfiguration
import no.nav.sif.abac.kontrakt.abac.BeskyttetRessursActionAttributt
import no.nav.sif.abac.kontrakt.abac.ResourceType
import no.nav.sif.abac.kontrakt.abac.dto.PersonerOperasjonDto
import no.nav.sif.abac.kontrakt.abac.resultat.Tilgangsbeslutning
import no.nav.sifinnsynapi.sikkerhet.AuthorizationConfig
import no.nav.sifinnsynapi.sikkerhet.abac.SifAbacPdpService
import no.nav.sifinnsynapi.sikkerhet.abac.TilgangskontrollService
import no.nav.sifinnsynapi.soknad.InnsendingService
import no.nav.sifinnsynapi.soknad.PsbSøknadDAO
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_INGEN_SAK
import no.nav.sifinnsynapi.util.CallIdGenerator
import no.nav.sifinnsynapi.utils.hentToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@EnableMockOAuth2Server
@Import(CallIdGenerator::class, SecurityConfiguration::class, AuthorizationConfig::class, TilgangskontrollService::class)
@WebMvcTest(controllers = [SaksbehandlingEndringsmeldingTilgangController::class])
@ActiveProfiles("test")
class SaksbehandlingEndringsmeldingTilgangControllerTest {

    private companion object {
        const val SØKER_AKTØR_ID = "1000000000002"
        const val PLEIETRENGENDE_AKTØR_ID = "1000000000003"

        // To innsendinger for samme barn
        val SØKNADER = listOf(søknadDAO("1"), søknadDAO("2"))

        fun søknadDAO(journalpostId: String) = PsbSøknadDAO(
            journalpostId = journalpostId,
            søkerAktørId = SØKER_AKTØR_ID,
            pleietrengendeAktørId = PLEIETRENGENDE_AKTØR_ID,
            søknad = "{}",
        )

        // Fra application-test.yml
        const val K9_SAK_CLIENT_ID = "k9-sak-client-id"
        const val ANNEN_PREAUTORISERT_CLIENT_ID = "sif-innsyn-api-client-id"

        //language=json
        const val BODY = """{ "søkerAktørId": "$SØKER_AKTØR_ID" }"""
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private lateinit var mockOAuth2Server: MockOAuth2Server

    @MockkBean
    private lateinit var endringsmeldingTilgangService: EndringsmeldingTilgangService

    @MockkBean(relaxed = true)
    private lateinit var auditlogger: Auditlogger

    @MockkBean
    private lateinit var sifAbacPdpService: SifAbacPdpService

    @MockkBean
    private lateinit var innsendingService: InnsendingService

    @BeforeEach
    fun setUp() {
        every { innsendingService.hentSøknader(SØKER_AKTØR_ID) } returns SØKNADER
        every { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(any()) } returns Tilgangsbeslutning(true, emptySet())
    }

    @Test
    fun `sif-abac-pdp får søker og pleietrengende med lesetilgang til fagsak`() {
        every { endringsmeldingTilgangService.vurderTilgang(SØKER_AKTØR_ID, SØKNADER) } returns
            TilgangResultat.ingenTilgang(listOf(HAR_INGEN_SAK))

        mockMvc.perform(kall(azureToken(azp = K9_SAK_CLIENT_ID)))
            .andExpect(status().isOk)

        val forespørsel = slot<PersonerOperasjonDto>()
        verify { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(capture(forespørsel)) }
        assertEquals(listOf(SØKER_AKTØR_ID, PLEIETRENGENDE_AKTØR_ID), forespørsel.captured.aktørIder().map { it.aktørId })
        assertEquals(ResourceType.FAGSAK, forespørsel.captured.operasjon().resource())
        assertEquals(BeskyttetRessursActionAttributt.READ, forespørsel.captured.operasjon().action())
    }

    @Test
    fun `tilgangssjekken og vurderingen bruker søknadene fra samme oppslag`() {
        every { endringsmeldingTilgangService.vurderTilgang(SØKER_AKTØR_ID, SØKNADER) } returns
            TilgangResultat.ingenTilgang(listOf(HAR_INGEN_SAK))

        mockMvc.perform(kall(azureToken(azp = K9_SAK_CLIENT_ID)))
            .andExpect(status().isOk)

        verify(exactly = 1) { innsendingService.hentSøknader(SØKER_AKTØR_ID) }
        verify(exactly = 1) { endringsmeldingTilgangService.vurderTilgang(SØKER_AKTØR_ID, SØKNADER) }
    }

    @Test
    fun `avslag fra sif-abac-pdp gir 403 uten audit-logg eller vurdering`() {
        every { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(any()) } returns
            Tilgangsbeslutning(false, setOf(ÅrsakIkkeTilgang.HAR_IKKE_TILGANG_ANNEN_GRUNN))

        mockMvc.perform(kall(azureToken(azp = K9_SAK_CLIENT_ID)))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { auditlogger.logg(any()) }
        verify(exactly = 0) { endringsmeldingTilgangService.vurderTilgang(any(), any()) }
    }

    @Test
    fun `feil mot sif-abac-pdp gir 500 uten audit-logg eller vurdering`() {
        every { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(any()) } throws IllegalStateException("nede")

        mockMvc.perform(kall(azureToken(azp = K9_SAK_CLIENT_ID)))
            .andExpect(status().isInternalServerError)

        verify(exactly = 0) { auditlogger.logg(any()) }
        verify(exactly = 0) { endringsmeldingTilgangService.vurderTilgang(any(), any()) }
    }

    @Test
    fun `saksbehandler får vurderingen for søkeren, no-store og audit-logg`() {
        every { endringsmeldingTilgangService.vurderTilgang(SØKER_AKTØR_ID, SØKNADER) } returns
            TilgangResultat.ingenTilgang(listOf(HAR_INGEN_SAK))

        mockMvc.perform(kall(azureToken(azp = K9_SAK_CLIENT_ID)))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            //language=json
            .andExpect(content().json("""{ "kanBruke": false, "årsaker": ["harIngenSak"], "tillattEndringsperiode": null }""", true))

        verify(exactly = 1) { auditlogger.logg(any()) }
    }

    @Test
    fun `kall fra en annen pre-autorisert app gir 403 uten audit-logg eller vurdering`() {
        mockMvc.perform(kall(azureToken(azp = ANNEN_PREAUTORISERT_CLIENT_ID)))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { auditlogger.logg(any()) }
        verify(exactly = 0) { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(any()) }
        verify(exactly = 0) { endringsmeldingTilgangService.vurderTilgang(any(), any()) }
    }

    @Test
    fun `azure-token uten NAVident gir 401`() {
        mockMvc.perform(kall(azureToken(azp = K9_SAK_CLIENT_ID, claims = emptyMap())))
            .andExpect(status().isUnauthorized)

        verify(exactly = 0) { endringsmeldingTilgangService.vurderTilgang(any(), any()) }
    }

    @Test
    fun `tokenx-token gir 401`() {
        val token = mockOAuth2Server.hentToken().serialize()

        mockMvc.perform(kall(token))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `ugyldig aktørId gir 400 uten å vurdere tilgang`() {
        mockMvc.perform(
            post(SAKSBEHANDLING_PSB_ENDRINGSMELDING_TILGANG)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${azureToken(azp = K9_SAK_CLIENT_ID)}")
                .content("""{ "søkerAktørId": "12345678910" }""")
        )
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { endringsmeldingTilgangService.vurderTilgang(any(), any()) }
    }

    private fun azureToken(azp: String, claims: Map<String, String> = mapOf("NAVident" to "Z999999")) =
        // issueToken uten clientId overskriver azp med "default", så azp må settes som clientId
        mockOAuth2Server.issueToken(
            issuerId = Issuers.AZURE,
            clientId = azp,
            tokenCallback = DefaultOAuth2TokenCallback(
                issuerId = Issuers.AZURE,
                audience = listOf("aud-localhost"),
                claims = claims,
            ),
        ).serialize()

    private fun kall(token: String) = post(SAKSBEHANDLING_PSB_ENDRINGSMELDING_TILGANG)
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .content(BODY)
}
