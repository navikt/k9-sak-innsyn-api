package no.nav.sifinnsynapi.soknad

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.k9.søknad.Søknad
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.Routes.SØKNAD
import no.nav.sifinnsynapi.config.SecurityConfiguration
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.util.CallIdGenerator
import no.nav.sifinnsynapi.utils.hentToken
import org.junit.Assert.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
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
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import java.time.LocalDate
import java.util.*
import javax.servlet.http.Cookie


@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test.
@Import(CallIdGenerator::class, SecurityConfiguration::class)
@WebMvcTest(controllers = [SøknadController::class])
@ActiveProfiles("test")
class SøknadControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var mockOAuth2Server: MockOAuth2Server

    @MockkBean(relaxed = true)
    lateinit var søknadService: SøknadService

    @BeforeAll
    internal fun setUp() {
        assertNotNull(mockOAuth2Server)
    }

    @Test
    fun `internal server error gir 500 med forventet problem-details`() {
        every {
            søknadService.hentSøknadsopplysningerPerBarn()
        } throws Exception("Ooops, noe gikk galt...")

        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(SØKNAD, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken().serialize()}")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.type").value("/problem-details/internal-server-error"))
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    fun `internal server error gir 500 med forventet problem-details i header`() {
        every {
            søknadService.hentSøknadsopplysningerPerBarn()
        } throws Exception("Ooops, noe gikk galt...")

        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(SØKNAD, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken().serialize()}")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isInternalServerError)
            .andExpect(header().exists("problem-details"))
            .andExpect(
                header().string(
                    "problem-details",
                    //language=json
                    """{"type":"/problem-details/internal-server-error","title":"Internal Server Error","status":500,"detail":"Ooops, noe gikk galt..."}""".trimIndent()
                )
            )
    }

    @Test
    fun `Gitt 200 respons, forvent korrekt format på liste av søknader`() {
        val søknadId = UUID.randomUUID().toString()
        every {
            søknadService.hentSøknadsopplysningerPerBarn()
        } returns listOf(
            SøknadDTO(
                barn = BarnOppslagDTO(
                    aktørId = "22222222222",
                    fødselsdato = LocalDate.parse("2005-02-12"),
                    fornavn = "Ole",
                    mellomnavn = null,
                    etternavn = "Doffen",
                    identitetsnummer = "12020567099"
                ),
                søknad = Søknad()
                    .medSøknadId(søknadId)
            )
        )


        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(SØKNAD, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken().serialize()}")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].barn").isMap)
            .andExpect(jsonPath("$[0].søknad").isMap)
            .andExpect(jsonPath("$[0].søknad.søknadId").value(søknadId))
    }

    @Test
    fun `Gitt 200 respons, forvent korrekt format på liste av søknader med tokenx token`() {
        val søknadId = UUID.randomUUID().toString()
        every {
            søknadService.hentSøknadsopplysningerPerBarn()
        } returns listOf(
            SøknadDTO(
                barn = BarnOppslagDTO(
                    aktørId = "22222222222",
                    fødselsdato = LocalDate.parse("2005-02-12"),
                    fornavn = "Ole",
                    mellomnavn = null,
                    etternavn = "Doffen",
                    identitetsnummer = "12020567099"
                ),
                søknad = Søknad()
                    .medSøknadId(søknadId)
            )
        )


        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(SØKNAD, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken(issuerId = "tokenx").serialize()}")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].barn").isMap)
            .andExpect(jsonPath("$[0].søknad").isMap)
            .andExpect(jsonPath("$[0].søknad.søknadId").value(søknadId))
    }

    @Test
    fun `gitt request uten token, forevnt 401`() {
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(SØKNAD, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("no.nav.security.token.support.core.exceptions.JwtTokenMissingException: no valid token found in validation context"))
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    fun `gitt request med token utsedt av annen issuer, forevnt 401`() {
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(SØKNAD, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .cookie(
                    Cookie(
                        "selvbetjening-idtoken",
                        mockOAuth2Server.hentToken(issuerId = "ukjent issuer").serialize()
                    )
                )
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("no.nav.security.token.support.core.exceptions.JwtTokenMissingException: no valid token found in validation context"))
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    fun `gitt request med token med ukjent audience, forevnt 401`() {
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(SØKNAD, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .cookie(
                    Cookie(
                        "selvbetjening-idtoken",
                        mockOAuth2Server.hentToken(audience = "ukjent audience").serialize()
                    )
                )
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("no.nav.security.token.support.core.exceptions.JwtTokenMissingException: no valid token found in validation context"))
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }
}
