package no.nav.sifinnsynapi.konsument.k9sak

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.SpykBean
import io.mockk.verify
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.config.kafka.Topics
import no.nav.sifinnsynapi.utils.leggPåTopic
import no.nav.sifinnsynapi.utils.opprettK9SakKafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension

@EmbeddedKafka( // Setter opp og tilgjengligjør embeded kafka broker
    count = 3,
    topics = [Topics.K9_SAK_TOPIC],
    bootstrapServersProperty = "kafka.aiven.servers" // Setter bootstrap-servers for consumer og producer.
)
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // Integrasjonstest - Kjører opp hele Spring Context med alle konfigurerte beans.
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test. Se application-test.yml -> no.nav.security.jwt.issuer.selvbetjening for konfigurasjon
@AutoConfigureWireMock // Konfigurerer og setter opp en wiremockServer. Default leses src/test/resources/__files og src/test/resources/mappings
class KafkaTransactionRollbackTest {

    @Autowired
    lateinit var mapper: ObjectMapper

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker // Broker som brukes til å konfigurere opp en kafka producer.

    @SpykBean
    lateinit var k9SakHendelseKonsument: K9SakHendelseKonsument

    lateinit var k9SakProducer: Producer<String, String> // Kafka producer som brukes til å legge på kafka meldinger.

    @BeforeAll
    fun setUp() {
        k9SakProducer = embeddedKafkaBroker.opprettK9SakKafkaProducer()
    }

    @AfterAll
    fun tearDown() {
        k9SakProducer.close()
    }

    @Test
    fun `Konsumering av hendelse som feiler skal forsøkes på nytt`() {
        val hendelse = hendelseMedBådeSøknadOgEttersendelse()
        k9SakProducer.leggPåTopic(hendelse, Topics.K9_SAK_TOPIC)

        // forvent at det som ble persistert blir rullet tilbake.
        verify(atLeast = 3, timeout = 60_000) { k9SakHendelseKonsument.konsumer(any()) }
    }

    // language=json
    private fun hendelseMedBådeSøknadOgEttersendelse() = """
                {
                  "data" : {
                    "type" : "PSB_SØKNADSINNHOLD",
                    "ettersendelse" : {
                        "mottattDato" : "2024-05-03T09:28:21.201Z",
                        "søker" : {
                            "norskIdentitetsnummer" : "14026223262"
                        },
                        "søknadId" : "67bcff24-82ec-47d9-a39b-2dae93c9bf64",
                        "ytelse" : "PLEIEPENGER_SYKT_BARN"
                    },
                    "journalpostId" : "1",
                    "pleietrengendeAktørId" : "22222222222",
                    "søkerAktørId" : "11111111111",
                    "søknad" : {
                      "mottattDato" : "2024-05-03T09:28:21.201Z",
                      "språk" : "nb",
                      "søker" : {
                        "norskIdentitetsnummer" : "14026223262"
                      },
                      "søknadId" : "67bcff24-82ec-47d9-a39b-2dae93c9bf64",
                      "versjon" : "1.0.0",
                      "ytelse" : {
                        "type" : "PLEIEPENGER_SYKT_BARN",
                        "annetDataBruktTilUtledning" : null,
                        "arbeidstid" : {
                          "arbeidstakerList" : [ {
                            "arbeidstidInfo" : {
                              "perioder" : {
                                "2021-08-01/2021-10-11" : {
                                  "faktiskArbeidTimerPerDag" : "PT4H",
                                  "jobberNormaltTimerPerDag" : "PT8H"
                                }
                              }
                            },
                            "norskIdentitetsnummer" : null,
                            "organisasjonsnavn" : null,
                            "organisasjonsnummer" : "987654321"
                          } ],
                          "frilanserArbeidstidInfo" : null,
                          "selvstendigNæringsdrivendeArbeidstidInfo" : null
                        },
                        "barn" : {
                          "fødselsdato" : null,
                          "norskIdentitetsnummer" : "21121879023"
                        },
                        "beredskap" : {
                          "perioder" : { },
                          "perioderSomSkalSlettes" : { }
                        },
                        "bosteder" : {
                          "perioder" : { },
                          "perioderSomSkalSlettes" : { }
                        },
                        "dataBruktTilUtledning" : null,
                        "endringsperiode" : [ ],
                        "infoFraPunsj" : null,
                        "lovbestemtFerie" : {
                          "perioder" : { }
                        },
                        "nattevåk" : {
                          "perioder" : { },
                          "perioderSomSkalSlettes" : { }
                        },
                        "omsorg" : {
                          "beskrivelseAvOmsorgsrollen" : null,
                          "relasjonTilBarnet" : null
                        },
                        "opptjeningAktivitet" : { },
                        "søknadsperiode" : [ "2024-05-08/2024-05-08" ],
                        "tilsynsordning" : {
                          "perioder" : { }
                        },
                        "trekkKravPerioder" : [ ],
                        "utenlandsopphold" : {
                          "perioder" : { },
                          "perioderSomSkalSlettes" : { }
                        },
                        "uttak" : {
                          "perioder" : { }
                        }
                      },
                      "begrunnelseForInnsending" : {
                        "tekst" : null
                      },
                      "journalposter" : [ {
                        "inneholderInfomasjonSomIkkeKanPunsjes" : null,
                        "inneholderInformasjonSomIkkeKanPunsjes" : null,
                        "inneholderMedisinskeOpplysninger" : null,
                        "journalpostId" : "123456789"
                      } ],
                      "kildesystem" : null
                    }
                  },
                  "oppdateringstidspunkt" : "2024-05-03T09:28:21.193Z"
                }
            """.trimIndent()
}
