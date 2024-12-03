package no.nav.sifinnsynapi.oppslag

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.size
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.security.token.support.spring.validation.interceptor.BearerTokenClientHttpRequestInterceptor
import no.nav.sifinnsynapi.http.MDCValuesPropagatingClientHttpRequestInterceptor
import no.nav.sifinnsynapi.util.Constants
import no.nav.sifinnsynapi.utils.stubSystemoppslagForHentBarn
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.util.*

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.allow-bean-definition-overriding=true"]
)
@AutoConfigureWireMock
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
                .defaultMessageConverters()
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
}
