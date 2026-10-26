package no.nav.sifinnsynapi.endringsmelding.psb

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.Routes.ENDRINGSMELDING_PSB_TILGANG
import no.nav.sifinnsynapi.config.SecurityConfiguration
import no.nav.sifinnsynapi.endringsmelding.psb.IngenTilgangÅrsak.EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD
import no.nav.sifinnsynapi.endringsmelding.psb.IngenTilgangÅrsak.HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE
import no.nav.sifinnsynapi.util.CallIdGenerator
import no.nav.sifinnsynapi.utils.hentToken
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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

        mockMvc.get(ENDRINGSMELDING_PSB_TILGANG) {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken().serialize()}")
        }.andExpect {
            status { isOk() }
            header { string("Cache-Control", "no-store") }
            content {
                //language=json
                json("""{ "kanBruke": true, "årsaker": [] }""", JsonCompareMode.STRICT)
            }
        }
    }

    @Test
    fun `avslag gir 200 med årsakskodene i samme rekkefølge`() {
        every { endringsmeldingTilgangService.vurderTilgang() } returns TilgangResultat.ingenTilgang(
            listOf(HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE, EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD)
        )

        mockMvc.get(ENDRINGSMELDING_PSB_TILGANG) {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken().serialize()}")
        }.andExpect {
            status { isOk() }
            content {
                //language=json
                json(
                    """
                    {
                      "kanBruke": false,
                      "årsaker": [
                        "harArbeidstidSomSelvstendigNæringsdrivende",
                        "enArbeidsgiverToAnsettelserSammeUkeMedOpphold"
                      ]
                    }
                    """.trimIndent(),
                    JsonCompareMode.STRICT
                )
            }
        }
    }

    @Test
    fun `uten token gir 401`() {
        mockMvc.get(ENDRINGSMELDING_PSB_TILGANG).andExpect { status { isUnauthorized() } }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Level3", "idporten-loa-substantial"])
    fun `utilstrekkelig acr gir 401`(acr: String) {
        mockMvc.get(ENDRINGSMELDING_PSB_TILGANG) {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken(claims = mapOf("acr" to acr)).serialize()}")
        }.andExpect { status { isUnauthorized() } }
    }
}
