package no.nav.sifinnsynapi.drift

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.k9.søknad.Søknad
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.Routes.SØKNAD
import no.nav.sifinnsynapi.audit.Auditlogger
import no.nav.sifinnsynapi.config.Issuers
import no.nav.sifinnsynapi.config.SecurityConfiguration
import no.nav.sifinnsynapi.oppslag.HentIdenterRespons
import no.nav.sifinnsynapi.oppslag.Ident
import no.nav.sifinnsynapi.oppslag.IdentGruppe
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.sikkerhet.AuthorizationConfig
import no.nav.sifinnsynapi.soknad.DebugDTO
import no.nav.sifinnsynapi.util.CallIdGenerator
import no.nav.sifinnsynapi.utils.hentToken
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.*


@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test.
@Import(CallIdGenerator::class, SecurityConfiguration::class, AuthorizationConfig::class)
@WebMvcTest(controllers = [DriftController::class])
@ActiveProfiles("test")
class DriftControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var mockOAuth2Server: MockOAuth2Server

    @MockkBean(relaxed = true)
    lateinit var driftService: DriftService

    @MockkBean(relaxed = true)
    lateinit var auditlogger: Auditlogger

    @MockkBean(relaxed = true)
    lateinit var oppslagsService: OppslagsService

    @BeforeAll
    internal fun setUp() {
        assertNotNull(mockOAuth2Server)
    }

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

        val token = mockOAuth2Server.hentToken(issuerId = "azure", claims = mapOf("NAVident" to "Z99481")).serialize()

        mockMvc.perform(
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
            .andExpect(header().string("problem-details", errorResponse))
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

        val token = mockOAuth2Server.hentToken(issuerId = "azure", claims = mapOf("NAVident" to "Z99481")).serialize()

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
