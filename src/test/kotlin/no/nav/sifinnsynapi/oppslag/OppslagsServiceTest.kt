package no.nav.sifinnsynapi.oppslag

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.size
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.security.token.support.spring.validation.interceptor.BearerTokenClientHttpRequestInterceptor
import no.nav.sifinnsynapi.http.MDCValuesPropagatingClientHttpRequestInterceptor
import no.nav.sifinnsynapi.util.Constants
import no.nav.sifinnsynapi.utils.stubSystemoppslagForHentBarn
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock
import java.time.Duration
import java.time.LocalDate
import java.util.*

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.allow-bean-definition-overriding=true"]
)
@EnableWireMock(ConfigureWireMock())
@ActiveProfiles("test")
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test. Se application-test.yml -> no.nav.security.jwt.issuer.selvbetjening for konfigurasjon
internal class OppslagsServiceTest {

    @TestConfiguration
    class TestConfig {

        @Value("\${no.nav.gateways.k9-selvbetjening-oppslag}")
        lateinit var oppslagsUrl: String

        @Bean(name = ["k9OppslagsKlient"])
        @Primary
        fun restTemplate(
            builder: RestTemplateBuilder,
            tokenInterceptor: BearerTokenClientHttpRequestInterceptor,
            mdcInterceptor: MDCValuesPropagatingClientHttpRequestInterceptor,
        ): RestTemplate {
            return builder
                .connectTimeout(Duration.ofSeconds(20))
                .readTimeout(Duration.ofSeconds(20))
                .defaultHeader(Constants.X_CORRELATION_ID, UUID.randomUUID().toString())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, anyString())
                .defaultHeader("X-K9-Ytelse", "PLEIEPENGER_SYKT_BARN")
                .rootUri(oppslagsUrl)
                .build()
        }
    }

    @Autowired
    lateinit var oppslagsService: OppslagsService

    @Test
    fun hentAktørId() {
        val hentAktørId = oppslagsService.hentSøker()
        assertThat(hentAktørId).isNotNull()
    }

    @Test
    fun hentBarn() {
        val barn = oppslagsService.hentBarn()
        assertThat(barn).size().isEqualTo(2)
    }

    @Test
    fun `hent barn med systemoppslag`() {
        val barnIdent = "12345678901"
        stubSystemoppslagForHentBarn(ident = barnIdent, 200)

        val barn = oppslagsService.systemoppslagBarn(HentBarnForespørsel(listOf(barnIdent)))
        assertThat(barn).size().isEqualTo(1)
    }

    @Test
    fun `hent adressebeskyttet barn med systemoppslag`() {
        val barnIdent = "12345678901"
        stubSystemoppslagForHentBarn(
            barnIdent,
            200,
            adressebeskyttelse = listOf(Adressebeskyttelse(AdressebeskyttelseGradering.STRENGT_FORTROLIG))
        )

        val barn = oppslagsService.systemoppslagBarn(HentBarnForespørsel(listOf(barnIdent)))
        assertThat(barn).isEmpty()
    }

    @Test
    fun `hent arbeidsgivere ber om alle ansettelsesperioder for perioden og ignorerer ukjente felt`() {
        stubArbeidsgivere(
            status = 200,
            //language=json
            body = """
                {
                  "arbeidsgivere": {
                    "organisasjoner": [
                      { "organisasjonsnummer": "111", "navn": "Ignoreres", "ansatt_fom": "2024-01-01", "ansatt_tom": "2024-01-10" },
                      { "organisasjonsnummer": "111", "ansatt_fom": "2024-01-15", "ansatt_tom": null }
                    ]
                  }
                }
            """.trimIndent()
        )

        val respons = oppslagsService.hentArbeidsgivere(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-31"))

        assertThat(respons.arbeidsgivere.organisasjoner).isEqualTo(
            listOf(
                OrganisasjonOppslagDTO("111", LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-10")),
                OrganisasjonOppslagDTO("111", LocalDate.parse("2024-01-15"), null),
            )
        )
    }

    @Test
    fun `feil ved henting av arbeidsgivere gir exception med fast melding, ikke tom liste`() {
        stubArbeidsgivere(status = 500, body = "{}")

        val feil = assertThrows<IllegalStateException> {
            oppslagsService.hentArbeidsgivere(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-31"))
        }
        assertThat(feil.message).isEqualTo("Feil ved henting av arbeidsgivere")
    }

    private fun stubArbeidsgivere(status: Int, body: String) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/k9-selvbetjening-oppslag-mock/meg"))
                .withQueryParam("fom", WireMock.equalTo("2024-01-01"))
                .withQueryParam("tom", WireMock.equalTo("2024-01-31"))
                .withQueryParam("inkluderAlleAnsettelsesperioder", WireMock.equalTo("true"))
                .withQueryParam(
                    "a",
                    WireMock.havingExactly(
                        "arbeidsgivere[].organisasjoner[].organisasjonsnummer",
                        "arbeidsgivere[].organisasjoner[].ansettelsesperiode"
                    )
                )
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(status)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(body)
                )
        )
    }
}
