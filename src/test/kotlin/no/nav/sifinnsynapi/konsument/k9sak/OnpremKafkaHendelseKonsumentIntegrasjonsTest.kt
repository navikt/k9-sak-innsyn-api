package no.nav.sifinnsynapi.konsument.k9sak

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotZero
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.k9.søknad.JsonUtils
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.Routes.SØKNAD
import no.nav.sifinnsynapi.SifInnsynApiApplication
import no.nav.sifinnsynapi.common.AktørId
import no.nav.sifinnsynapi.config.SecurityConfiguration
import no.nav.sifinnsynapi.config.Topics.K9_SAK_TOPIC
import no.nav.sifinnsynapi.soknad.SøknadDTO
import no.nav.sifinnsynapi.soknad.SøknadRepository
import no.nav.sifinnsynapi.utils.*
import org.apache.kafka.clients.producer.Producer
import org.awaitility.kotlin.await
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@EmbeddedKafka( // Setter opp og tilgjengligjør embeded kafka broker.
    count = 3,
    bootstrapServersProperty = "kafka.aiven.servers", // Setter bootstrap-servers for consumer og producer.
    topics = [K9_SAK_TOPIC]
)
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
@ActiveProfiles("test")
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test. Se application-test.yml -> no.nav.security.jwt.issuer.selvbetjening for konfigurasjon
@Import(SecurityConfiguration::class)
@AutoConfigureWireMock // Konfigurerer og setter opp en wiremockServer. Default leses src/test/resources/__files og src/test/resources/mappings
@SpringBootTest(
    classes = [SifInnsynApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
) // Integrasjonstest - Kjører opp hele Spring Context med alle konfigurerte beans.
class OnpremKafkaHendelseKonsumentIntegrasjonsTest {

    @Autowired
    lateinit var mapper: ObjectMapper

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker // Broker som brukes til å konfigurere opp en kafka producer.

    @Autowired
    lateinit var repository: SøknadRepository // Repository som brukes til databasekall.

    @Autowired
    lateinit var restTemplate: TestRestTemplate // Restklient som brukes til å gjøre restkall mot endepunkter i appen.

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var mockOAuth2Server: MockOAuth2Server

    lateinit var k9SakProducer: Producer<String, String> // Kafka producer som brukes til å legge på kafka meldinger.

    companion object {
        private val log: Logger =
            LoggerFactory.getLogger(OnpremKafkaHendelseKonsumentIntegrasjonsTest::class.java)
        private val aktørId = AktørId.valueOf("123456")
    }

    @BeforeAll
    fun setUp() {
        repository.deleteAll()
        assertNotNull(mockOAuth2Server)
        k9SakProducer = embeddedKafkaBroker.opprettK9SakKafkaProducer()
    }

    @BeforeEach
    internal fun beforeEach() {
        log.info("Tømmer databasen...")
        repository.deleteAll()
    }

    @AfterEach
    fun afterEach() {
        log.info("Tømmer databasen...")
        repository.deleteAll()
    }

    @AfterAll
    fun tearDown() {
        repository.deleteAll()
        k9SakProducer.close()
    }

    @Test
    fun `Forvent riktig konsumering og persistering av innsynshendelse`() {

        // legg på 1 hendelse om mottatt hendelse fra k9-sak...
        k9SakProducer.leggPåTopic(defaultPsbSøknadInnholdHendelse(journalpostId = "1"), K9_SAK_TOPIC, JsonUtils.getObjectMapper())

        // forvent at mottatt hendelse konsumeres og persisteres, samt at gitt restkall gitt forventet resultat.
        await.atMost(10, TimeUnit.SECONDS).untilAsserted {
            assertThat(repository.findById("1")).isNotNull()
        }
    }

    @Test
    @Disabled("Disabler foreløpig")
    fun `Konsumere k9-sak hendelse, persister og tilgjengligjør gjennom API`() {
        repository.deleteAll()

        // legg på 1 hendelse om mottatt  hendelse fra k9-sak...
        val hendelse = defaultPsbSøknadInnholdHendelse()
        val søknadId = hendelse.data.søknad.søknadId.id
        val søkerPersonIdent = hendelse.data.søknad.søker.personIdent

        k9SakProducer.leggPåTopic(hendelse, K9_SAK_TOPIC, mapper)

        // forvent at mottatt hendelse konsumeres og persisteres, samt at gitt restkall gitt forventet resultat.
        await.atMost(10, TimeUnit.SECONDS).untilAsserted {
            val responseEntity = restTemplate.exchange(
                SØKNAD,
                HttpMethod.GET,
                hentToken(),
                object : ParameterizedTypeReference<List<SøknadDTO>>() {})
            val forventetRespons =
                //language=json
                """
                   [
                     {
                       "søknadId": "$søknadId",
                       "søknad": {
                          "søknadId": "$søknadId",
                          "versjon": null,
                          "mottattDato": null,
                          "søker": {
                            "norskIdentitetsnummer": "$søkerPersonIdent"
                          },
                          "språk": "nb",
                          "ytelse": null,
                              "journalposter": [ ]
                      }
                     }
                   ]
                    """.trimIndent()

            responseEntity.listAssert(forventetRespons, 200)
        }
    }

    @Test
    @Disabled
    fun `Konsumere k9-sak hendelse, persister, og hent søknad med id`() {

        // legg på 1 hendelse om mottatt hendelse fra k9-sak...
        val hendelse = defaultPsbSøknadInnholdHendelse()
        val søknadId = hendelse.data.søknad.søknadId.id
        val søkerPersonIdent = hendelse.data.søknad.søker.personIdent
        k9SakProducer.leggPåTopic(hendelse, K9_SAK_TOPIC, mapper)

        // forvent at mottatt hendelse konsumeres og persisteres, samt at gitt restkall gitt forventet resultat.
        await.atMost(10, TimeUnit.SECONDS).untilAsserted {
            val responseEntity =
                restTemplate.exchange("${SØKNAD}/${søknadId}", HttpMethod.GET, hentToken(), SøknadDTO::class.java)
            val forventetRespons =
                //language=json
                """
                      {
                          "søknadId" : "$søknadId",
                          "søknad": {
                              "søknadId" : "$søknadId",
                              "versjon" : null,
                              "mottattDato" : null,
                              "søker" : {
                                "norskIdentitetsnummer" : "$søkerPersonIdent"
                              },
                              "språk" : "nb",
                              "ytelse" : null,
                              "journalposter" : [ ]
                          }
                      }
                    """.trimIndent()
            responseEntity.assert(forventetRespons, 200)
        }
    }

    private fun ResponseEntity<List<SøknadDTO>>.listAssert(
        forventetResponse: String,
        forventetStatus: Int,
        compareMode: JSONCompareMode = JSONCompareMode.LENIENT
    ) {
        assertThat(statusCodeValue).isEqualTo(forventetStatus)
        JSONAssert.assertEquals(forventetResponse, body!!.somJson(mapper), compareMode)
    }

    private fun ResponseEntity<SøknadDTO>.assert(
        forventetResponse: String,
        forventetStatus: Int,
        compareMode: JSONCompareMode = JSONCompareMode.LENIENT
    ) {
        assertThat(statusCodeValue).isEqualTo(forventetStatus)
        JSONAssert.assertEquals(forventetResponse, body!!.somJson(mapper), compareMode)
    }

    private fun hentToken(personIdentifikator: String = "12345678910"): HttpEntity<String> =
        mockOAuth2Server.hentToken(subject = personIdentifikator).tokenTilHttpEntity()
}
