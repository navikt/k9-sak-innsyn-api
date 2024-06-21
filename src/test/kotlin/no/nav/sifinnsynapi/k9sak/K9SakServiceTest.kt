package no.nav.sifinnsynapi.k9sak

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.k9.sak.typer.Saksnummer
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenResponse
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.common.AktørId
import no.nav.sifinnsynapi.utils.hentToken
import org.junit.Assert
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.allow-bean-definition-overriding=true"]
)
@AutoConfigureWireMock
@ActiveProfiles("test")
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test. Se application-test.yml -> no.nav.security.jwt.issuer.selvbetjening for konfigurasjon
internal class K9SakServiceTest {

    @Autowired
    lateinit var k9SakService: K9SakService

    @Autowired
    lateinit var mockOAuth2Server: MockOAuth2Server

    @MockkBean
    lateinit var oAuth2AccessTokenService: OAuth2AccessTokenService


    private companion object {
        private val omsorgsdagerKroniskSyktBarnHarGyldigVedtakUrl =
            "/k9-sak-mock/k9/sak/api/brukerdialog/omsorgsdager-kronisk-sykt-barn/har-gyldig-vedtak"
    }

    @BeforeEach
    fun setUp() {
        var token = mockOAuth2Server.hentToken("123456789", audience = "dev-fss:k9saksbehandling:k9-sak").serialize()
        every { oAuth2AccessTokenService.getAccessToken(any()) } returns OAuth2AccessTokenResponse(token)
    }

    @Test
    fun `k9-sak svarer som forventet`() {
        val pleietrengendeAktør = AktørId("2345")

        stubK9Sak(
            pleietrengendeAktør, 200,
            // language=JSON
            """
            { 
              "harInnvilgedeBehandlinger": true,            
              "saksnummer": "${Saksnummer("12345678").verdi}",
              "vedtaksdato": "2024-05-21"
            }
            """.trimIndent()
        )

        k9SakService.hentSisteGyldigeVedtakForAktorId(
            HentSisteGyldigeVedtakForAktorIdDto(
                pleietrengendeAktør
            )
        )
    }

    @Test
    fun `k9-sak svarer med ingen behandling funnet`() {
        val pleietrengendeAktør = AktørId("2345")

        stubK9Sak(
            pleietrengendeAktør, 200,
            // language=JSON
            """
            { 
              "harInnvilgedeBehandlinger": false,            
              "saksnummer": null,
              "vedtaksdato": null
            }
            """.trimIndent()
        )

        k9SakService.hentSisteGyldigeVedtakForAktorId(
            HentSisteGyldigeVedtakForAktorIdDto(
                pleietrengendeAktør
            )
        )
    }

    @Test
    fun `server exceptions blir håndtert`() {
        val pleietrengendeAktør = AktørId("2345")

        stubK9Sak(
            pleietrengendeAktør, 500,
            // language=JSON
            """
            {
              "type": "GENERELL_FEIL",
              "feilmelding": "Det oppstod en serverfeil",
              "feltFeil": []
            }
            """.trimIndent()
        )

        val resultat = k9SakService.hentSisteGyldigeVedtakForAktorId(
            HentSisteGyldigeVedtakForAktorIdDto(
                pleietrengendeAktør
            )
        )

        WireMock.verify(
            3,
            postRequestedFor(urlEqualTo(omsorgsdagerKroniskSyktBarnHarGyldigVedtakUrl))
        )

        Assertions.assertEquals(
            resultat, HentSisteGyldigeVedtakForAktorIdResponse(
                harInnvilgedeBehandlinger = false, saksnummer = null, vedtaksdato = null
            )
        )
    }

    @Test
    fun `klient exceptions blir håndtert`() {
        val pleietrengendeAktør = AktørId("2345")

        stubK9Sak(
            pleietrengendeAktør, 401,
            // language=JSON
            """
            {
              "type": "GENERELL_FEIL",
              "feilmelding": "Det oppstod en serverfeil",
              "feltFeil": []
            }
            """.trimIndent()
        )

        val resultat = k9SakService.hentSisteGyldigeVedtakForAktorId(
            HentSisteGyldigeVedtakForAktorIdDto(
                pleietrengendeAktør
            )
        )

        WireMock.verify(
            1,
            postRequestedFor(urlEqualTo(omsorgsdagerKroniskSyktBarnHarGyldigVedtakUrl))
        )

        Assertions.assertEquals(
            resultat, HentSisteGyldigeVedtakForAktorIdResponse(
                harInnvilgedeBehandlinger = false, saksnummer = null, vedtaksdato = null
            )
        )
    }


    private fun stubK9Sak(pleietrengendeAktørId: AktørId, status: Int, responseBody: String) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlPathMatching(omsorgsdagerKroniskSyktBarnHarGyldigVedtakUrl))
                .withHeader("Authorization", WireMock.matching(".*"))
                .withRequestBody(
                    WireMock.equalToJson(
                        //language=json
                        """
                    {
                      "pleietrengendeAktørId": "${pleietrengendeAktørId.aktørId}"
                    }
                """.trimIndent()
                    )
                )
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(status)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(responseBody)
                )
        )
    }
}

