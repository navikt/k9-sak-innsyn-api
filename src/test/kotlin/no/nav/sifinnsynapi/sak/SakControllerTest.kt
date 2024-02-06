package no.nav.sifinnsynapi.sak

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.Routes
import no.nav.sifinnsynapi.config.Issuers
import no.nav.sifinnsynapi.config.SecurityConfiguration
import no.nav.sifinnsynapi.util.CallIdGenerator
import no.nav.sifinnsynapi.utils.hentToken
import org.junit.jupiter.api.Assertions
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import java.time.LocalDate

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test.
@Import(CallIdGenerator::class, SecurityConfiguration::class)
@WebMvcTest(controllers = [SakController::class])
@ActiveProfiles("test")
class SakControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var mockOAuth2Server: MockOAuth2Server

    @MockkBean(relaxed = true)
    lateinit var sakService: SakService

    @BeforeAll
    internal fun setUp() {
        Assertions.assertNotNull(mockOAuth2Server)
    }

    @Test
    fun `internal server error gir 500 med forventet problem-details`() {
        every {
            sakService.hentSaker()
        } throws Exception("Ooops, noe gikk galt...")

        //language=json
        val errorResponse =
            """{"type":"/problem-details/internal-server-error","title":"Et uventet feil har oppstått","status":500,"detail":"Ooops, noe gikk galt...","instance":"http://localhost/saker"}""".trimIndent()

        val token = mockOAuth2Server.hentToken().serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(Routes.SAKER, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isInternalServerError)
            .andExpect(MockMvcResultMatchers.header().exists("problem-details"))
            .andExpect(MockMvcResultMatchers.content().json(errorResponse))
            .andExpect(MockMvcResultMatchers.header().string("problem-details", errorResponse))
    }

    @Test
    fun `Gitt 200 respons, forvent korrekt format på liste av saker med tokenx token`() {
        every {
            sakService.hentSaker()
        } returns listOf(
            SakDTO(
                saksbehandlingsFrist = LocalDate.now().plusDays(10)
            )
        )

        val token = mockOAuth2Server.hentToken().serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(Routes.SAKER, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.jsonPath("$[0].saksbehandlingsFrist")
                    .value(LocalDate.now().plusDays(10).toString())
            )
    }

    @Test
    fun `Ved henting av generell saksbehandlingstid uten token, forvent 8 uker`() {
        every {
            sakService.hentGenerellSaksbehandlingstid()
        } returns SaksbehandlingtidDTO(saksbehandlingstidUker = 8)

        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode("${Routes.SAKER}/saksbehandlingstid", Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.saksbehandlingstidUker").value(8)
            )
    }

    @ParameterizedTest
    @ValueSource(strings = [Issuers.AZURE, "ukjent"])
    fun `gitt request med token utsedt av annen issuer enn idporten eller tokenx, forevnt 401`(issuer: String) {
        val token = mockOAuth2Server.hentToken(issuerId = issuer, claims = mapOf()).serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(Routes.SAKER, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(401))
            .andExpect(MockMvcResultMatchers.jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    fun `gitt request uten token, forevnt 401`() {
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(Routes.SAKER, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(401))
            .andExpect(MockMvcResultMatchers.jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    fun `gitt request med token med ukjent audience, forevnt 401`() {
        val token = mockOAuth2Server.hentToken(audience = "ukjent audience").serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(Routes.SAKER, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(401))
            .andExpect(MockMvcResultMatchers.jsonPath("$.stackTrace").doesNotExist())
    }
}
