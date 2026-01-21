package no.nav.sifinnsynapi.soknad

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.k9.søknad.Søknad
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.Routes.SØKNAD
import no.nav.sifinnsynapi.config.Issuers.AZURE
import no.nav.sifinnsynapi.config.SecurityConfiguration
import no.nav.sifinnsynapi.oppslag.Adressebeskyttelse
import no.nav.sifinnsynapi.oppslag.AdressebeskyttelseGradering
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.util.CallIdGenerator
import no.nav.sifinnsynapi.utils.hentToken
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.skyscreamer.jsonassert.JSONAssert
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
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import java.time.LocalDate
import java.util.*


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
    lateinit var innsendingService: InnsendingService

    @BeforeAll
    internal fun setUp() {
        assertNotNull(mockOAuth2Server)
    }

    @Test
    fun `internal server error gir 500 med forventet problem-details`() {
        every {
            innsendingService.slåSammenSøknadsopplysningerPerBarn()
        } throws Exception("Ooops, noe gikk galt...")

        //language=json
        val errorResponse =
            """{"type":"/problem-details/internal-server-error","title":"Et uventet feil har oppstått","status":500,"detail":"Ooops, noe gikk galt...","instance":"http://localhost/soknad"}""".trimIndent()

        val token = mockOAuth2Server.hentToken().serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(SØKNAD, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isInternalServerError)
            .andExpect(header().exists("problem-details"))
            .andExpect(content().json(errorResponse))
            .andExpect {
                val actualHeader = it.response.getHeader("problem-details")
                JSONAssert.assertEquals(errorResponse, actualHeader, false)
            }
    }

    @Test
    fun `Gitt 200 respons, forvent korrekt format på liste av søknader med tokenx token`() {
        val søknadId = UUID.randomUUID().toString()
        every {
            innsendingService.slåSammenSøknadsopplysningerPerBarn()
        } returns listOf(
            SøknadDTO(
                barn = BarnOppslagDTO(
                    fødselsdato = LocalDate.parse("2022-11-01"),
                    fornavn = "Ola",
                    mellomnavn = null,
                    etternavn = "Nordmann",
                    aktørId = "123",
                    identitetsnummer = "12020567099",
                    adressebeskyttelse = listOf(
                        Adressebeskyttelse(gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG)
                    )
                ),
                søknad = Søknad()
                    .medSøknadId(søknadId)
            )
        )

        val token = mockOAuth2Server.hentToken().serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(SØKNAD, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].barn").isMap)
            .andExpect(jsonPath("$[0].barn.adressebeskyttelse").doesNotExist())
            .andExpect(jsonPath("$[0].søknad").isMap)
            .andExpect(jsonPath("$[0].søknad.søknadId").value(søknadId))
    }

    @ParameterizedTest
    @ValueSource(strings = [AZURE, "ukjent"])
    fun `gitt request med token utsedt av annen issuer enn idporten eller tokenx, forevnt 401`(issuer: String) {
        val token = mockOAuth2Server.hentToken(issuerId = issuer, claims = mapOf()).serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(SØKNAD, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
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
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    fun `gitt request med token med ukjent audience, forevnt 401`() {
        val token = mockOAuth2Server.hentToken(audience = "ukjent audience").serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(SØKNAD, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    fun `forvent generert filnavn med mellomrom`() {
        every {
            innsendingService.hentArbeidsgiverMeldingFil(any(), any())
        } returns "some byteArray".toByteArray()

        val forventetFilnavn = "Bekreftelse_til_arbeidsgiver_12345678.pdf"
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode("$SØKNAD/${UUID.randomUUID()}/arbeidsgivermelding", Charset.defaultCharset())))
                .queryParam("organisasjonsnummer", "12345678")
                .accept(MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken().serialize()}")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk)
            .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=$forventetFilnavn"))
    }
}
