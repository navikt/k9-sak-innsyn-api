package no.nav.sifinnsynapi

import com.ninjasquad.springmockk.MockkBean
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.config.Issuers
import no.nav.sifinnsynapi.dokumentoversikt.DokumentService
import no.nav.sifinnsynapi.drift.DriftService
import no.nav.sifinnsynapi.k9sak.K9SakService
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.sak.SakService
import no.nav.sifinnsynapi.sak.inntektsmelding.InntektsmeldingService
import no.nav.sifinnsynapi.sikkerhet.AuthorizationService
import no.nav.sifinnsynapi.sikkerhet.abac.TilgangskontrollService
import no.nav.sifinnsynapi.soknad.InnsendingService
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.EndringsmeldingTilgangService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ValueConstants
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock
import java.util.*

/**
 * Automatisk sikkerhetstest for alle REST-endepunkter i applikasjonen.
 *
 * Forventet tilgangsstyring er definert eksplisitt i [forventetTilgang], og testen feiler dersom et endepunkt
 * mangler der. Nye endepunkter må dermed få en bevisst vurdering av tilgangsstyring.
 *
 * For hvert beskyttet endepunkt verifiseres det at:
 * - kall uten token, med utløpt token, feil audience eller ukjent issuer avvises (401)
 * - kall med token fra en issuer som ikke er tillatt avvises (401), for alle konfigurerte issuers
 * - kall med tillatt issuer, men manglende/ugyldige claims avvises (401)
 * - kall med tillatt issuer og gyldige claims slipper gjennom tilgangskontrollen (ikke 401/403)
 */
@SpringBootTest(classes = [SifInnsynApiApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@EnableMockOAuth2Server
@EnableWireMock(ConfigureWireMock())
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationTests {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var mockOAuth2Server: MockOAuth2Server

    @Autowired
    private lateinit var multiIssuerConfiguration: MultiIssuerConfiguration

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    @MockkBean(relaxed = true)
    private lateinit var sakService: SakService

    @MockkBean(relaxed = true)
    private lateinit var inntektsmeldingService: InntektsmeldingService

    @MockkBean(relaxed = true)
    private lateinit var dokumentService: DokumentService

    @MockkBean(relaxed = true)
    private lateinit var k9SakService: K9SakService

    @MockkBean(relaxed = true)
    private lateinit var innsendingService: InnsendingService

    @MockkBean(relaxed = true)
    private lateinit var endringsmeldingTilgangService: EndringsmeldingTilgangService

    @MockkBean(relaxed = true)
    private lateinit var driftService: DriftService

    @MockkBean(relaxed = true)
    private lateinit var oppslagsService: OppslagsService

    @MockkBean(relaxed = true)
    private lateinit var tilgangskontrollService: TilgangskontrollService

    @MockkBean(relaxed = true)
    private lateinit var authorizationService: AuthorizationService

    private sealed interface Tilgang {
        data object Ubeskyttet : Tilgang

        data class Beskyttet(
            val issuer: String,
            val gyldigeClaims: List<Map<String, String>>,
            val ugyldigeClaims: List<Map<String, String>>,
        ) : Tilgang
    }

    private val tokenXMedHøyttSikkerhetsnivå = Tilgang.Beskyttet(
        issuer = Issuers.TOKEN_X,
        gyldigeClaims = listOf(mapOf("acr" to "Level4"), mapOf("acr" to "idporten-loa-high")),
        ugyldigeClaims = listOf(emptyMap(), mapOf("acr" to "Level3"), mapOf("acr" to "idporten-loa-substantial")),
    )

    private val azureMedNavIdent = Tilgang.Beskyttet(
        issuer = Issuers.AZURE,
        gyldigeClaims = listOf(mapOf("NAVident" to "Z123456")),
        ugyldigeClaims = listOf(emptyMap()),
    )

    private val azure = Tilgang.Beskyttet(
        issuer = Issuers.AZURE,
        gyldigeClaims = listOf(emptyMap()),
        ugyldigeClaims = emptyList(),
    )

    private val forventetTilgang: Map<String, Tilgang> = mapOf(
        "GET /saker" to tokenXMedHøyttSikkerhetsnivå,
        "GET /saker/metadata" to tokenXMedHøyttSikkerhetsnivå,
        "GET /saker/saksbehandlingstid" to Tilgang.Ubeskyttet,
        "GET /sak/{saksnummer}" to tokenXMedHøyttSikkerhetsnivå,
        "GET /sak/{saksnummer}/inntektsmeldinger" to tokenXMedHøyttSikkerhetsnivå,
        "GET /soknad" to tokenXMedHøyttSikkerhetsnivå,
        "GET /soknad/{søknadId}/arbeidsgivermelding" to tokenXMedHøyttSikkerhetsnivå,
        "GET /dokument/{journalpostId}/{dokumentInfoId}/{variantFormat}" to tokenXMedHøyttSikkerhetsnivå,
        "GET /psb/endringsmelding/tilgang" to tokenXMedHøyttSikkerhetsnivå,
        "POST /k9sak/omsorgsdager-kronisk-sykt-barn/har-gyldig-vedtak" to tokenXMedHøyttSikkerhetsnivå,
        "GET /k9sak/opplaringsinstitusjoner" to tokenXMedHøyttSikkerhetsnivå,
        "GET /k9sak/opplaringsinstitusjoner/aktive" to tokenXMedHøyttSikkerhetsnivå,
        "POST /saksbehandling/psb/endringsmelding/tilgang" to azureMedNavIdent,
        "POST /debug/soknad" to azure,
        "POST /forvaltning/oppdaterAktoerId" to azureMedNavIdent,
    )

    private val parameterverdier = mapOf(
        "saksnummer" to "ABC123",
        "søknadId" to UUID.randomUUID().toString(),
        "journalpostId" to "123456789",
        "dokumentInfoId" to "123456789",
        "variantFormat" to "ARKIV",
        "organisasjonsnummer" to "123456789",
        "dokumentTittel" to "tittel",
    )

    private data class Endepunkt(
        val metode: HttpMethod,
        val sti: String,
        val påkrevdeQueryParametere: List<String>,
        val harRequestBody: Boolean,
    ) {
        val nøkkel = "${metode.name()} $sti"
    }

    private fun endepunkter(): List<Endepunkt> = handlerMapping.handlerMethods
        .filter { (_, handler) -> handler.beanType.packageName.startsWith("no.nav.sifinnsynapi") }
        .flatMap { (info, handler) -> info.tilEndepunkter(handler) }
        .sortedBy { it.nøkkel }

    private fun RequestMappingInfo.tilEndepunkter(handler: HandlerMethod): List<Endepunkt> {
        val påkrevdeQueryParametere = handler.methodParameters
            .mapNotNull { p -> p.getParameterAnnotation(RequestParam::class.java)?.let { p to it } }
            .filter { (_, annotasjon) -> annotasjon.required && annotasjon.defaultValue == ValueConstants.DEFAULT_NONE }
            .map { (p, annotasjon) -> annotasjon.name.ifBlank { annotasjon.value }.ifBlank { p.parameterName!! } }
        val harRequestBody = handler.methodParameters.any { it.hasParameterAnnotation(RequestBody::class.java) }

        return methodsCondition.methods.flatMap { metode ->
            directPaths.ifEmpty { patternValues }.map { sti ->
                Endepunkt(HttpMethod.valueOf(metode.name), sti, påkrevdeQueryParametere, harRequestBody)
            }
        }
    }

    @Test
    fun `alle endepunkter har definert forventet tilgangsstyring`() {
        val faktiske = endepunkter().map { it.nøkkel }.toSet()
        assertEquals(
            forventetTilgang.keys.sorted(), faktiske.sorted(),
            "Endepunktene i applikasjonen samsvarer ikke med forventetTilgang. " +
                    "Legg til nye endepunkter med en bevisst vurdering av tilgangsstyring."
        )
    }

    @Test
    fun `alle konfigurerte issuers dekkes av testen`() {
        assertEquals(setOf(Issuers.TOKEN_X, Issuers.AZURE), multiIssuerConfiguration.issuerShortNames.toSet())
    }

    @TestFactory
    fun `tilgangsstyring for alle endepunkter og issuers`(): List<DynamicNode> = endepunkter()
        .filter { forventetTilgang.containsKey(it.nøkkel) }
        .map { endepunkt ->
            val tester = when (val tilgang = forventetTilgang.getValue(endepunkt.nøkkel)) {
                Tilgang.Ubeskyttet -> listOf(
                    DynamicTest.dynamicTest("uten token gir tilgang") {
                        endepunkt.kall(token = null).forventTilgang()
                    }
                )

                is Tilgang.Beskyttet -> beskyttetEndepunktTester(endepunkt, tilgang)
            }
            DynamicContainer.dynamicContainer(endepunkt.nøkkel, tester)
        }

    private fun beskyttetEndepunktTester(endepunkt: Endepunkt, tilgang: Tilgang.Beskyttet): List<DynamicTest> {
        val gyldigeClaims = tilgang.gyldigeClaims.first()
        val alleGyldigeClaims = tilgang.gyldigeClaims.fold(emptyMap<String, String>()) { acc, claims -> acc + claims } +
                tokenXMedHøyttSikkerhetsnivå.gyldigeClaims.first() + azureMedNavIdent.gyldigeClaims.first()

        val avvisningstester = listOf(
            DynamicTest.dynamicTest("uten token gir 401") {
                endepunkt.kall(token = null).forventAvvist()
            },
            DynamicTest.dynamicTest("utløpt token gir 401") {
                endepunkt.kall(token(tilgang.issuer, gyldigeClaims, expiry = -60)).forventAvvist()
            },
            DynamicTest.dynamicTest("feil audience gir 401") {
                endepunkt.kall(token(tilgang.issuer, gyldigeClaims, audience = "feil-audience")).forventAvvist()
            },
            DynamicTest.dynamicTest("ukjent issuer gir 401") {
                endepunkt.kall(token("ukjent-issuer", alleGyldigeClaims)).forventAvvist()
            },
        )

        val andreIssuers = multiIssuerConfiguration.issuerShortNames
            .filterNot { it == tilgang.issuer }
            .map { issuer ->
                DynamicTest.dynamicTest("issuer $issuer gir 401") {
                    endepunkt.kall(token(issuer, alleGyldigeClaims)).forventAvvist()
                }
            }

        val ugyldigeClaims = tilgang.ugyldigeClaims.map { claims ->
            DynamicTest.dynamicTest("issuer ${tilgang.issuer} med claims $claims gir 401") {
                endepunkt.kall(token(tilgang.issuer, claims)).forventAvvist()
            }
        }

        val gyldige = tilgang.gyldigeClaims.map { claims ->
            DynamicTest.dynamicTest("issuer ${tilgang.issuer} med claims $claims gir tilgang") {
                endepunkt.kall(token(tilgang.issuer, claims)).forventTilgang()
            }
        }

        return avvisningstester + andreIssuers + ugyldigeClaims + gyldige
    }

    private fun token(
        issuer: String,
        claims: Map<String, String>,
        audience: String = "aud-localhost",
        expiry: Long = 3600,
    ): String = mockOAuth2Server.issueToken(
        issuerId = issuer,
        subject = "12345678910",
        audience = audience,
        claims = claims,
        expiry = expiry,
    ).serialize()

    private fun Endepunkt.kall(token: String?): HttpStatus {
        val headers = HttpHeaders().apply {
            token?.let { setBearerAuth(it) }
            if (harRequestBody) contentType = MediaType.APPLICATION_JSON
        }
        val body = if (harRequestBody) "{}" else null
        val query = påkrevdeQueryParametere.joinToString(separator = "&", prefix = "?") { "$it={$it}" }
            .takeIf { påkrevdeQueryParametere.isNotEmpty() } ?: ""

        val variabler = Regex("\\{([^}]+)}").findAll(sti + query).map { it.groupValues[1] }.associateWith { navn ->
            requireNotNull(parameterverdier[navn]) { "Mangler testverdi for parameter '$navn' i $nøkkel" }
        }

        val respons = restTemplate.exchange(sti + query, metode, HttpEntity(body, headers), String::class.java, variabler)
        return HttpStatus.valueOf(respons.statusCode.value())
    }

    private fun HttpStatus.forventAvvist() =
        assertEquals(HttpStatus.UNAUTHORIZED, this, "Forventet 401, men fikk $this")

    private fun HttpStatus.forventTilgang() =
        assertTrue(this != HttpStatus.UNAUTHORIZED && this != HttpStatus.FORBIDDEN, "Forventet tilgang, men fikk $this")
}
