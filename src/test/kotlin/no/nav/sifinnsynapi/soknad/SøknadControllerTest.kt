package no.nav.sifinnsynapi.soknad

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
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
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.http.ContentDisposition
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
                    identitetsnummer = "23500180528",
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

    @ParameterizedTest
    @ValueSource(strings = ["Level4", "idporten-loa-high"])
    fun `gitt request med token med tilstrekkelig acr, forvent 200`(acr: String) {
        every {
            innsendingService.slåSammenSøknadsopplysningerPerBarn()
        } returns emptyList()

        val token = mockOAuth2Server.hentToken(claims = mapOf("acr" to acr)).serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(SØKNAD, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Level3", "idporten-loa-substantial"])
    fun `gitt request med token med utilstrekkelig acr, forvent 401`(acr: String) {
        val token = mockOAuth2Server.hentToken(claims = mapOf("acr" to acr)).serialize()
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

    @ParameterizedTest
    @ValueSource(strings = ["0", "0123456789", "12345678"])
    fun `forvent generert filnavn for numerisk organisasjonsnummer`(organisasjonsnummer: String) {
        every {
            innsendingService.hentArbeidsgiverMeldingFil(any(), any())
        } returns "some byteArray".toByteArray()

        val forventetFilnavn = "Bekreftelse_til_arbeidsgiver_$organisasjonsnummer.pdf"
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode("$SØKNAD/${UUID.randomUUID()}/arbeidsgivermelding", Charset.defaultCharset())))
                .queryParam("organisasjonsnummer", organisasjonsnummer)
                .accept(MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken().serialize()}")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk)
            .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
            .andExpect { result ->
                val disposition = ContentDisposition.parse(result.response.getHeader(HttpHeaders.CONTENT_DISPOSITION)!!)
                assertEquals("inline", disposition.type)
                assertEquals(forventetFilnavn, disposition.filename)
            }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "", " ", "1234567a", "1234 5678", "12345678 ", "1234567\u0668", "\uFF11\uFF12\uFF13",
        "12345678; injected=value", "12345678\"; filename=\"injected", "12345678\\\"", "12345678; filename*=UTF-8''injected",
        "12345678\r\nX-Injected: true", "12345678\r", "12345678\n", "12345678\u0000", "12345678\t", "12345678\u007f", "12345678\u0085",
    ])
    fun `avviser organisasjonsnummer utenfor sifrene 0-9 før arbeidsgivermelding hentes`(organisasjonsnummer: String) {
        mockMvc.perform(
            MockMvcRequestBuilders
                .get("$SØKNAD/${UUID.randomUUID()}/arbeidsgivermelding")
                .queryParam("organisasjonsnummer", organisasjonsnummer)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken().serialize()}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
            .andExpect(header().doesNotExist("X-Injected"))

        verify(exactly = 0) { innsendingService.hentArbeidsgiverMeldingFil(any(), any()) }
    }
}
