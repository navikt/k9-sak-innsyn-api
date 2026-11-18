package no.nav.sifinnsynapi.dokumentoversikt

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.Routes
import no.nav.sifinnsynapi.config.Issuers
import no.nav.sifinnsynapi.config.SecurityConfiguration
import no.nav.sifinnsynapi.util.CallIdGenerator
import no.nav.sifinnsynapi.utils.hentToken
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test.
@Import(CallIdGenerator::class, SecurityConfiguration::class)
@WebMvcTest(controllers = [DokumentController::class])
@ActiveProfiles("test")
internal class DokumentControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var mockOAuth2Server: MockOAuth2Server

    @MockkBean(relaxed = true)
    lateinit var dokumentService: DokumentService

    @BeforeAll
    internal fun setUp() {
        assertNotNull(mockOAuth2Server)
    }

    @Test
    fun `hent dokument`() {
        val forventetFilnavn = "Screenshot 2021-04-23 at 12.59.57.pdf"
        every {
            dokumentService.hentDokument(any(), any(), any())
        } returns ArkivertDokument(
            body = "some byteArray".toByteArray(),
            contentType = "application/pdf",
            contentDisposition = ContentDisposition.parse("inline; filename=533439502_ARKIV.pdf")
        )

        mockMvc.perform(
            MockMvcRequestBuilders
                .get("${Routes.DOKUMENT}/{journalpostId}/{dokumentinfoId}/{variant}", "123456789", "987654321", "ARKIV")
                .queryParam("dokumentTittel", "Screenshot 2021-04-23 at 12.59.57.png")
                .accept(MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken().serialize()}")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk)
            .andExpect(MockMvcResultMatchers.header().exists(HttpHeaders.CONTENT_DISPOSITION))
            .andExpect { result ->
                val disposition = ContentDisposition.parse(result.response.getHeader(HttpHeaders.CONTENT_DISPOSITION)!!)
                assertEquals("inline", disposition.type)
                assertEquals(forventetFilnavn, disposition.filename)
            }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "rapport; injected=value.pdf",
        "rapport\"; filename=\"injected.pdf",
        "rapport; filename*=UTF-8''injected%2Epdf",
        "rapport\\\".pdf",
        "Søknad med blåbær.pdf",
    ])
    fun `dokumenttittel behandles som filnavn og ikke headerparametere`(tittel: String) {
        every { dokumentService.hentDokument(any(), any(), any()) } returns ArkivertDokument(
            body = "pdf".toByteArray(),
            contentType = "application/pdf",
            contentDisposition = ContentDisposition.parse("inline; filename=dokument.pdf")
        )

        mockMvc.perform(
            MockMvcRequestBuilders
                .get("${Routes.DOKUMENT}/123456789/987654321/ARKIV")
                .queryParam("dokumentTittel", tittel)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken().serialize()}")
        )
            .andExpect(status().isOk)
            .andExpect { result ->
                val header = result.response.getHeader(HttpHeaders.CONTENT_DISPOSITION)!!
                val disposition = ContentDisposition.parse(header)
                assertEquals("inline", disposition.type)
                assertEquals(tittel, disposition.filename)
                assertTrue(header.all { it.code in 32..126 })
            }
    }

    @ParameterizedTest
    @ValueSource(strings = ["rapport\r\nX-Injected: true.pdf", "rapport\r.pdf", "rapport\n.pdf", "rapport\u0000.pdf", "rapport\t.pdf", "rapport\u007f.pdf", "rapport\u0085.pdf"])
    fun `avviser kontrolltegn i dokumenttittel før dokument hentes`(tittel: String) {
        mockMvc.perform(
            MockMvcRequestBuilders
                .get("${Routes.DOKUMENT}/123456789/987654321/ARKIV")
                .queryParam("dokumentTittel", tittel)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken().serialize()}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
            .andExpect(MockMvcResultMatchers.header().doesNotExist("X-Injected"))

        verify(exactly = 0) { dokumentService.hentDokument(any(), any(), any()) }
    }

    @Test
    fun `hent dokument med tokenx-token uten påkrevd acr-nivå, forvent 401`() {
        val token = mockOAuth2Server.hentToken(claims = mapOf("acr" to "Level3")).serialize()

        mockMvc.perform(
            MockMvcRequestBuilders
                .get("${Routes.DOKUMENT}/{journalpostId}/{dokumentinfoId}/{variant}", "123456789", "987654321", "ARKIV")
                .queryParam("dokumentTittel", "dokument.pdf")
                .accept(MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `hent dokument med azure-token, forvent 401`() {
        val token = mockOAuth2Server.hentToken(issuerId = Issuers.AZURE).serialize()

        mockMvc.perform(
            MockMvcRequestBuilders
                .get("${Routes.DOKUMENT}/{journalpostId}/{dokumentinfoId}/{variant}", "123456789", "987654321", "ARKIV")
                .queryParam("dokumentTittel", "dokument.pdf")
                .accept(MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `hent dokument med ugyldige url parametere`() {
        every {
            dokumentService.hentDokument(any(), any(), any())
        } returns ArkivertDokument(
            body = "some byteArray".toByteArray(),
            contentType = "application/pdf",
            contentDisposition = ContentDisposition.parse("inline; filename=533439502_ARKIV.pdf")
        )

        mockMvc.perform(
            MockMvcRequestBuilders
                .get("${Routes.DOKUMENT}/{journalpostId}/{dokumentinfoId}/{variant}", "123", "321", "ANNET")
                .queryParam("dokumentTittel", "Screenshot 2021-04-23 at 12.59.57.png")
                .accept(MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mockOAuth2Server.hentToken().serialize()}")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isBadRequest)
            .andExpect(
                content().json(
                    //language=json
                    """
                    {
                      "type": "/problem-details/ugyldig-forespørsel",
                      "instance": "http://localhost/dokument/123/321/ANNET",
                      "title": "Ugyldig forespørsel",
                      "status": 400,
                      "detail": "Forespørselen inneholder valideringsfeil",
                      "violations": [
                        {
                          "property": "hentDokument.dokumentInfoId",
                          "message":  "[321] matcher ikke tillatt pattern [\\d{9}]",
                          "invalidValue": "321"
                        },
                        {
                          "property": "hentDokument.journalpostId",
                          "message": "[123] matcher ikke tillatt pattern [\\d{9}]",
                          "invalidValue": "123"
                        },
                        {
                          "property": "hentDokument.variantFormat",
                          "message":  "[ANNET] matcher ikke tillatt pattern [ARKIV]",
                          "invalidValue": "ANNET"
                        }
                      ]
                    }
                """.trimIndent(), true
                )
            )
    }
}
