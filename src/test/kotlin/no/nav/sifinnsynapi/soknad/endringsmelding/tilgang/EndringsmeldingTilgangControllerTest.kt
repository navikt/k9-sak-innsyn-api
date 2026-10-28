package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.Routes.PSB_ENDRINGSMELDING_TILGANG
import no.nav.sifinnsynapi.config.SecurityConfiguration
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE
import no.nav.sifinnsynapi.util.CallIdGenerator
import no.nav.sifinnsynapi.utils.hentToken
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@EnableMockOAuth2Server
@Import(CallIdGenerator::class, SecurityConfiguration::class)
@WebMvcTest(controllers = [EndringsmeldingTilgangController::class])
@ActiveProfiles("test")
class EndringsmeldingTilgangControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private lateinit var mockOAuth2Server: MockOAuth2Server

    @MockkBean
    private lateinit var endringsmeldingTilgangService: EndringsmeldingTilgangService

    @Test
    fun `tilgang gir kanBruke true, tom liste og no-store`() {
        every { endringsmeldingTilgangService.vurderTilgang() } returns TilgangResultat.tilgang()

        mockMvc.perform(get(PSB_ENDRINGSMELDING_TILGANG).header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            //language=json
            .andExpect(content().json("""{ "kanBruke": true, "årsaker": [] }""", true))
    }

    @Test
    fun `avslag gir 200 med årsakskodene i samme rekkefølge`() {
        every { endringsmeldingTilgangService.vurderTilgang() } returns TilgangResultat.ingenTilgang(
            listOf(HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE, EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD)
        )

        mockMvc.perform(get(PSB_ENDRINGSMELDING_TILGANG).header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    //language=json
                    """
                    {
                      "kanBruke": false,
                      "årsaker": [
                        "harArbeidstidSomSelvstendigNæringsdrivende",
                        "enArbeidsgiverToAnsettelserSammeUkeMedOpphold"
                      ]
                    }
                    """.trimIndent(),
                    true
                )
            )
    }

    @Test
    fun `token uten tilstrekkelig innloggingsnivå gir 401`() {
        val token = mockOAuth2Server.hentToken(claims = mapOf("acr" to "Level3")).serialize()

        mockMvc.perform(get(PSB_ENDRINGSMELDING_TILGANG).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isUnauthorized)
    }

    private fun bearer() = "Bearer ${mockOAuth2Server.hentToken().serialize()}"
}
