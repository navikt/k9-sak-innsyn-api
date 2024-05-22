package no.nav.sifinnsynapi.k9sak

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.k9.sak.typer.Saksnummer
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.config.SecurityConfiguration
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.oppslag.SøkerOppslagRespons
import no.nav.sifinnsynapi.util.CallIdGenerator
import no.nav.sifinnsynapi.utils.hentToken
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.LocalDate

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test.
@Import(CallIdGenerator::class, SecurityConfiguration::class)
@WebMvcTest(controllers = [K9SakController::class])
@ActiveProfiles("test")
class K9SakControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var mockOAuth2Server: MockOAuth2Server

    @MockkBean
    lateinit var k9SakService: K9SakService

    @MockkBean
    lateinit var oppslagsService: OppslagsService

    @Test
    fun `fungerer fint`() {
        every { k9SakService.hentSisteGyldigeVedtakForAktorId(any()) } returns HentSisteGyldigeVedtakForAktorIdResponse(
            harInnvilgedeBehandlinger = true,
            saksnummer = Saksnummer("123456"),
            vedtaksdato = LocalDate.now()
        )
        every { oppslagsService.hentSøker() } returns SøkerOppslagRespons(aktørId = "123456")

        mockMvc.post("/k9sak/omsorgsdager-kronisk-sykt-barn/har-gyldig-vedtak") {
            contentType = MediaType.APPLICATION_JSON
            headers { setBearerAuth(mockOAuth2Server.hentToken("123456789").serialize()) }
            content = """
                {
                    "pleietrengendeAktørId": "123456"
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            content {
                json(
                    """
                    {
                        "harInnvilgedeBehandlinger": true,
                        "saksnummer": "123456",
                        "vedtaksdato": "${LocalDate.now()}"
                    }
                    """.trimIndent()
                )
            }
        }
    }

    @Test
    fun `forventer 401 hvis vi mangler token`() {

        mockMvc.post("/k9sak/omsorgsdager-kronisk-sykt-barn/har-gyldig-vedtak") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "pleietrengendeAktørId": "123456"
                }
            """.trimIndent()
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `forventer å feile dersom aktørId mangler`() {
        every { oppslagsService.hentSøker() } throws IllegalStateException("Fant ikke aktørId for innlogget bruker")

        mockMvc.post("/k9sak/omsorgsdager-kronisk-sykt-barn/har-gyldig-vedtak") {
            contentType = MediaType.APPLICATION_JSON
            headers { setBearerAuth(mockOAuth2Server.hentToken("123456789").serialize()) }
            content = """
                {
                    "pleietrengendeAktørId": "123456"
                }
            """.trimIndent()
        }.andExpect {
            status { isInternalServerError() }
            content {
                json(
                    """
                    {
                      "type": "/problem-details/internal-server-error",
                      "title": "Et uventet feil har oppstått",
                      "status": 500,
                      "detail": "Fant ikke aktørId for innlogget bruker",
                      "instance": "http://localhost/k9sak/omsorgsdager-kronisk-sykt-barn/har-gyldig-vedtak"
                    }
                    """.trimIndent()
                )
            }
        }
    }

    @Test
    fun `forventer feil når kall mot k9 feiler`() {
        every { k9SakService.hentSisteGyldigeVedtakForAktorId(any()) } throws K9SakException("Ugyldig pleietrengendeAktørId", HttpStatus.BAD_REQUEST)

        every { oppslagsService.hentSøker() } returns SøkerOppslagRespons(aktørId = "123456")

        mockMvc.post("/k9sak/omsorgsdager-kronisk-sykt-barn/har-gyldig-vedtak") {
            contentType = MediaType.APPLICATION_JSON
            headers { setBearerAuth(mockOAuth2Server.hentToken("123456789").serialize()) }
            content = """
                {
                    "pleietrengendeAktørId": "123456"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            content {
                json(
                    """
                    {
                      "type": "/problem-details/k9-sak",
                      "title": "Feil ved kall mot k9-sak",
                      "status": 400,
                      "detail": "Ugyldig pleietrengendeAktørId",
                      "instance": "/k9sak/omsorgsdager-kronisk-sykt-barn/har-gyldig-vedtak"
                    }
                    """.trimIndent()
                )
            }
        }
    }
}

