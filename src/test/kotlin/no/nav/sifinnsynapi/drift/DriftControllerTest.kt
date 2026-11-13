package no.nav.sifinnsynapi.drift

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import no.nav.k9.felles.sikkerhet.abac.ÅrsakIkkeTilgang
import no.nav.k9.søknad.Søknad
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sif.abac.kontrakt.abac.BeskyttetRessursActionAttributt
import no.nav.sif.abac.kontrakt.abac.ResourceType
import no.nav.sif.abac.kontrakt.abac.dto.PersonerOperasjonDto
import no.nav.sif.abac.kontrakt.abac.resultat.Tilgangsbeslutning
import no.nav.sifinnsynapi.Routes.SØKNAD
import no.nav.sifinnsynapi.audit.Auditlogger
import no.nav.sifinnsynapi.config.Issuers
import no.nav.sifinnsynapi.config.SecurityConfiguration
import no.nav.sifinnsynapi.oppslag.HentIdenterRespons
import no.nav.sifinnsynapi.oppslag.Ident
import no.nav.sifinnsynapi.oppslag.IdentGruppe
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.sikkerhet.AuthorizationConfig
import no.nav.sifinnsynapi.sikkerhet.abac.SifAbacPdpService
import no.nav.sifinnsynapi.sikkerhet.abac.TilgangskontrollService
import no.nav.sifinnsynapi.soknad.DebugDTO
import no.nav.sifinnsynapi.util.CallIdGenerator
import no.nav.sifinnsynapi.utils.hentToken
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import tools.jackson.databind.ObjectMapper
import java.util.*


@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test.
@Import(CallIdGenerator::class, SecurityConfiguration::class, AuthorizationConfig::class, TilgangskontrollService::class)
@WebMvcTest(controllers = [DriftController::class])
@ActiveProfiles("test")
class DriftControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var mockOAuth2Server: MockOAuth2Server

    @MockkBean(relaxed = true)
    lateinit var driftService: DriftService

    @MockkBean(relaxed = true)
    lateinit var auditlogger: Auditlogger

    @MockkBean(relaxed = true)
    lateinit var oppslagsService: OppslagsService

    @MockkBean
    lateinit var sifAbacPdpService: SifAbacPdpService

    @BeforeAll
    internal fun setUp() {
        assertNotNull(mockOAuth2Server)
    }

    @BeforeEach
    fun gittTilgangFraPdp() {
        every { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(any()) } returns Tilgangsbeslutning(true, emptySet())
    }

    private fun gittIdenter() {
        every { oppslagsService.hentIdenter(any()) }
            .returns(listOf(HentIdenterRespons(ident = "123", identer = listOf(Ident("321", IdentGruppe.AKTORID)))))
            .andThen(listOf(HentIdenterRespons(ident = "456", identer = listOf(Ident("654", IdentGruppe.AKTORID)))))
    }

    private fun debugKall() = MockMvcRequestBuilders
        .post(debugSøknaderEndepunkt)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.AUTHORIZATION, "Bearer ${driftToken()}")
        .content(payload)

    @Test
    fun `sif-abac-pdp og oppslaget får søker og bare pleietrengende fra forespørselen`() {
        gittIdenter()

        mockMvc.perform(debugKall()).andExpect(status().isOk)

        val forespørsel = slot<PersonerOperasjonDto>()
        verify { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(capture(forespørsel)) }
        assertThat(forespørsel.captured.aktørIder().map { it.aktørId }).isEqualTo(listOf("321", "654"))
        assertThat(forespørsel.captured.operasjon().resource()).isEqualTo(ResourceType.FAGSAK)
        assertThat(forespørsel.captured.operasjon().action()).isEqualTo(BeskyttetRessursActionAttributt.READ)
        verify(exactly = 1) { driftService.slåSammenSøknadsopplysningerPerBarn("321", listOf("654"), emptyList()) }
    }

    @Test
    fun `avslag fra sif-abac-pdp gir 403 uten audit-logg eller søknadsdata`() {
        gittIdenter()
        every { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(any()) } returns
            Tilgangsbeslutning(false, setOf(ÅrsakIkkeTilgang.HAR_IKKE_TILGANG_TIL_KODE6_PERSON))

        mockMvc.perform(debugKall()).andExpect(status().isForbidden)

        verify(exactly = 0) { auditlogger.logg(any()) }
        verify(exactly = 0) { driftService.slåSammenSøknadsopplysningerPerBarn(any(), any(), any()) }
    }

    @Test
    fun `feil mot sif-abac-pdp gir 500 uten audit-logg eller søknadsdata`() {
        gittIdenter()
        every { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(any()) } throws IllegalStateException("nede")

        mockMvc.perform(debugKall()).andExpect(status().isInternalServerError)

        verify(exactly = 0) { auditlogger.logg(any()) }
        verify(exactly = 0) { driftService.slåSammenSøknadsopplysningerPerBarn(any(), any(), any()) }
    }

    private fun aktørByttekall() = MockMvcRequestBuilders
        .post("/forvaltning/oppdaterAktoerId")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.AUTHORIZATION, "Bearer ${driftToken()}")
        //language=json
        .content("""{ "utgåttAktør": "1000000000001", "gyldigAktør": "1000000000002" }""")

    @Test
    fun `aktørbytte sender gyldig og utgått aktør til sif-abac-pdp før oppdatering`() {
        every { driftService.oppdaterAktørId("1000000000002", "1000000000001") } returns 1

        mockMvc.perform(aktørByttekall()).andExpect(status().isOk)

        val forespørsel = slot<PersonerOperasjonDto>()
        verify { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(capture(forespørsel)) }
        assertThat(forespørsel.captured.aktørIder().map { it.aktørId }).isEqualTo(listOf("1000000000002", "1000000000001"))
        verify(exactly = 1) { driftService.oppdaterAktørId("1000000000002", "1000000000001") }
    }

    @Test
    fun `aktørbytte med avslag fra sif-abac-pdp gir 403 uten oppdatering eller audit-logg`() {
        every { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(any()) } returns
            Tilgangsbeslutning(false, setOf(ÅrsakIkkeTilgang.HAR_IKKE_TILGANG_TIL_KODE6_PERSON))

        mockMvc.perform(aktørByttekall()).andExpect(status().isForbidden)

        verify(exactly = 0) { driftService.oppdaterAktørId(any(), any()) }
        verify(exactly = 0) { auditlogger.logg(any()) }
    }

    @Test
    fun `aktørbytte med feil mot sif-abac-pdp gir 500 uten oppdatering`() {
        every { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(any()) } throws IllegalStateException("nede")

        mockMvc.perform(aktørByttekall()).andExpect(status().isInternalServerError)

        verify(exactly = 0) { driftService.oppdaterAktørId(any(), any()) }
    }
    private fun driftToken() = mockOAuth2Server.issueToken(
        issuerId = Issuers.AZURE,
        audience = "aud-localhost",
        claims = mapOf("NAVident" to "Z99481", "groups" to listOf("testdrift")),
    ).serialize()

    private companion object {
        private const val debugSøknaderEndepunkt = "/debug$SØKNAD"

        @Language("JSON")
        private val payload = """
                    {
                      "søkerNorskIdentitetsnummer": "123",
                      "pleietrengendeNorskIdentitetsnummer": ["456"]
                    }
                """.trimIndent()
    }

    @Test
    fun `internal server error gir 500 med forventet problem-details`() {
        every {
            oppslagsService.hentIdenter(any())
        } throws Exception("Ooops, noe gikk galt...")

        //language=json
        val errorResponse =
            """{"type":"/problem-details/internal-server-error","title":"Et uventet feil har oppstått","status":500,"detail":"Ooops, noe gikk galt...","instance":"http://localhost/debug/soknad"}""".trimIndent()

        val token = driftToken()

        val result = mockMvc.perform(
            MockMvcRequestBuilders
                .post(debugSøknaderEndepunkt)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .content(payload)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isInternalServerError)
            .andExpect(header().exists("problem-details"))
            .andExpect(content().json(errorResponse))
            .andReturn()

        val problemDetailHeader = result.response.getHeader("problem-details")

        assertThat(objectMapper.readTree(problemDetailHeader))
            .isEqualTo(objectMapper.readTree(errorResponse))
    }

    @Test
    fun `Gitt 200 respons, forvent korrekt format på liste av søknader`() {
        val søknadId = UUID.randomUUID().toString()
        every {
            driftService.slåSammenSøknadsopplysningerPerBarn(any(), any())
        } returns listOf(DebugDTO(pleietrengendeAktørId = "123", søknad = Søknad().medSøknadId(søknadId)))

        every {
            oppslagsService.hentIdenter(any())
        }
            .returns(listOf(HentIdenterRespons(ident = "123", identer = listOf(Ident("321", IdentGruppe.AKTORID)))))
            .andThen(listOf(HentIdenterRespons(ident = "456", identer = listOf(Ident("654", IdentGruppe.AKTORID)))))

        val token = driftToken()

        mockMvc.perform(
            MockMvcRequestBuilders
                .post(debugSøknaderEndepunkt)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token}")
                .content(payload)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].pleietrengendeAktørId").isString)
            .andExpect(jsonPath("$[0].søknad").isMap)
            .andExpect(jsonPath("$[0].søknad.søknadId").value(søknadId))
    }

    @Test
    fun `token uten drift-gruppe gir 403 uten oppslag eller audit-logg`() {
        val token = mockOAuth2Server.issueToken(
            issuerId = Issuers.AZURE,
            audience = "aud-localhost",
            claims = mapOf("NAVident" to "Z99481", "groups" to listOf("annen-gruppe")),
        ).serialize()

        mockMvc.perform(
            MockMvcRequestBuilders
                .post(debugSøknaderEndepunkt)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .content(payload)
        )
            .andExpect(status().isForbidden)

        verify(exactly = 0) { oppslagsService.hentIdenter(any()) }
        verify(exactly = 0) { sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(any()) }
        verify(exactly = 0) { auditlogger.logg(any()) }
    }

    @Test
    fun `gitt request uten token, forevnt 401`() {
        mockMvc.perform(
            MockMvcRequestBuilders
                .post(debugSøknaderEndepunkt)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @ParameterizedTest
    @ValueSource(strings = [Issuers.TOKEN_X])
    fun `gitt request med token utsedt av annen issuer enn azure, forevnt 401`(issuer: String) {
        val token = mockOAuth2Server.hentToken(issuerId = issuer).serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .post(debugSøknaderEndepunkt)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .content(payload)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    fun `gitt request med token med ukjent audience, forevnt 401`() {
        val token = mockOAuth2Server.hentToken(issuerId = "azure", audience = "ukjent audience").serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .post(debugSøknaderEndepunkt)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .content(payload)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }
}
