package no.nav.sifinnsynapi.k9sak

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import no.nav.k9.sak.typer.Saksnummer
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.common.AktørId
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

    private companion object {
        private val omsorgsdagerKroniskSyktBarnHarGyldigVedtakUrl =
            "/k9-sak-mock/k9/sak/api/brukerdialog/omsorgsdager-kronisk-sykt-barn/har-gyldig-vedtak"
    }

    @Test
    fun `k9-sak svarer som forventet`() {
        val aktør = AktørId("1234")
        val pleietrengendeAktør = AktørId("2345")

        stubK9Sak(
            aktør, pleietrengendeAktør, 200,
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
                aktør,
                pleietrengendeAktør
            )
        )
    }

    @Test
    fun `k9-sak svarer med ingen behandling funnet`() {
        val aktør = AktørId("1234")
        val pleietrengendeAktør = AktørId("2345")

        stubK9Sak(
            aktør, pleietrengendeAktør, 200,
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
                aktør,
                pleietrengendeAktør
            )
        )
    }

    @Test
    fun `server exceptions blir håndtert`() {
        val aktør = AktørId("1234")
        val pleietrengendeAktør = AktørId("2345")

        stubK9Sak(
            aktør, pleietrengendeAktør, 500,
            // language=JSON
            """
            {
              "type": "GENERELL_FEIL",
              "feilmelding": "Det oppstod en serverfeil",
              "feltFeil": []
            }
            """.trimIndent()
        )

        assertThrows<K9SakException> {
            k9SakService.hentSisteGyldigeVedtakForAktorId(
                HentSisteGyldigeVedtakForAktorIdDto(
                    aktør,
                    pleietrengendeAktør
                )
            )
        }

        WireMock.verify(
            3,
            postRequestedFor(urlEqualTo(omsorgsdagerKroniskSyktBarnHarGyldigVedtakUrl))
        )
    }

    @Test
    fun `klient exceptions blir håndtert`() {
        val aktør = AktørId("1234")
        val pleietrengendeAktør = AktørId("2345")

        stubK9Sak(
            aktør, pleietrengendeAktør, 401,
            // language=JSON
            """
            {
              "type": "GENERELL_FEIL",
              "feilmelding": "Det oppstod en serverfeil",
              "feltFeil": []
            }
            """.trimIndent()
        )

        assertThrows<K9SakException> {
            k9SakService.hentSisteGyldigeVedtakForAktorId(
                HentSisteGyldigeVedtakForAktorIdDto(
                    aktør,
                    pleietrengendeAktør
                )
            )
        }

        WireMock.verify(
            1,
            postRequestedFor(urlEqualTo(omsorgsdagerKroniskSyktBarnHarGyldigVedtakUrl))
        )
    }


    private fun stubK9Sak(aktørId: AktørId, pleietrengendeAktørId: AktørId, status: Int, responseBody: String) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlPathMatching(omsorgsdagerKroniskSyktBarnHarGyldigVedtakUrl))
                .withHeader("Authorization", WireMock.matching(".*"))
                .withRequestBody(
                    WireMock.equalToJson(
                        //language=json
                        """
                    {
                      "aktørId": "${aktørId.aktørId}",
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

