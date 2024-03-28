package no.nav.sifinnsynapi.konsument.k9sak

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.k9.søknad.felles.type.Periode
import no.nav.k9.søknad.ytelse.psb.v1.PleiepengerSyktBarn
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstid
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidPeriodeInfo
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.SifInnsynApiApplication
import no.nav.sifinnsynapi.config.SecurityConfiguration
import no.nav.sifinnsynapi.config.kafka.Topics.K9_SAK_TOPIC
import no.nav.sifinnsynapi.omsorg.OmsorgDAO
import no.nav.sifinnsynapi.omsorg.OmsorgRepository
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.*
import no.nav.sifinnsynapi.soknad.SøknadRepository
import no.nav.sifinnsynapi.soknad.SøknadService
import no.nav.sifinnsynapi.utils.*
import org.apache.kafka.clients.producer.Producer
import org.awaitility.kotlin.await
import org.junit.Assert.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.context.annotation.Import
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

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
class KafkaHendelseKonsumentIntegrasjonsTest {

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker // Broker som brukes til å konfigurere opp en kafka producer.

    @Autowired
    lateinit var søknadRepository: SøknadRepository // Repository som brukes til databasekall.

    @Autowired
    lateinit var søknadService: SøknadService

    @Autowired
    lateinit var restTemplate: TestRestTemplate // Restklient som brukes til å gjøre restkall mot endepunkter i appen.

    @Autowired
    lateinit var omsorgRepository: OmsorgRepository

    @Autowired
    lateinit var omsorgService: OmsorgService

    @MockkBean(relaxed = true)
    lateinit var oppslagsService: OppslagsService

    lateinit var k9SakProducer: Producer<String, String> // Kafka producer som brukes til å legge på kafka meldinger.

    companion object {
        private val logger: Logger =
            LoggerFactory.getLogger(KafkaHendelseKonsumentIntegrasjonsTest::class.java)

        private val hovedSøkerAktørId = "11111111111"
        private val barn1AktørId = "22222222222"
        private val barn2AktørId = "33333333333"
    }

    @BeforeAll
    fun setUp() {
        søknadRepository.deleteAll()
        omsorgRepository.deleteAll()
        k9SakProducer = embeddedKafkaBroker.opprettK9SakKafkaProducer()
    }

    @BeforeEach
    internal fun beforeEach() {
        logger.info("Tømmer databasen...")
        søknadRepository.deleteAll()
        every { oppslagsService.hentSøker() } returns SøkerOppslagRespons(aktørId = hovedSøkerAktørId)
        every { oppslagsService.hentBarn() } returns listOf(
            BarnOppslagDTO(
                aktørId = barn1AktørId,
                fødselsdato = LocalDate.parse("2005-02-12"),
                fornavn = "Ole",
                mellomnavn = null,
                etternavn = "Doffen",
                identitetsnummer = "12020567099"
            ),
            BarnOppslagDTO(
                aktørId = barn2AktørId,
                fødselsdato = LocalDate.parse("2005-10-30"),
                fornavn = "Dole",
                mellomnavn = null,
                etternavn = "Doffen",
                identitetsnummer = "30100577255"
            )
        )


        omsorgRepository.saveAll(
            listOf(
                OmsorgDAO(
                    id = "1",
                    søkerAktørId = hovedSøkerAktørId,
                    pleietrengendeAktørId = barn1AktørId,
                    harOmsorgen = true,
                    opprettetDato = ZonedDateTime.now(UTC),
                    oppdatertDato = ZonedDateTime.now(UTC)
                )
            )
        )
    }

    @AfterEach
    fun afterEach() {
        logger.info("Tømmer databasen...")
        søknadRepository.deleteAll()
        omsorgRepository.deleteAll()
    }

    @AfterAll
    fun tearDown() {
        søknadRepository.deleteAll()
        omsorgRepository.deleteAll()
        k9SakProducer.close()
    }

    @Test
    fun `Gitt søknad med en arbeidstaker konsumeres og persisteres, forvent riktige perioder ved sammenslåing`() {
        val org = "987654321"

        val psbSøknadInnholdHendelse = defaultPsbSøknadInnholdHendelse(
            journalpostId = "1",
            søkerAktørId = hovedSøkerAktørId,
            pleiepetrengendeAktørId = barn1AktørId,
            arbeidstid = Arbeidstid().medArbeidstaker(
                listOf(
                    defaultArbeidstaker(
                        organisasjonsnummer = org,
                        periode = Periode(LocalDate.parse("2021-08-01"), LocalDate.parse("2021-10-11")),
                        normaltTimerPerDag = 8,
                        faktiskArbeidTimerPerDag = 4
                    )
                )
            )
        )
        k9SakProducer.leggPåTopic(psbSøknadInnholdHendelse, K9_SAK_TOPIC)

        val forventetPSB = psbSøknadInnholdHendelse.data.søknad.getYtelse<PleiepengerSyktBarn>()

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val faktiskPSB =
                kotlin.runCatching {
                    søknadService.slåSammenSøknadsopplysningerPerBarn().first().søknad.getYtelse<PleiepengerSyktBarn>()
                }
                    .getOrNull()
            assertNotNull(faktiskPSB)

            assertThat(faktiskPSB!!.arbeidstid.arbeidstakerList.size)
                .isEqualTo(forventetPSB.arbeidstid.arbeidstakerList.size)

            assertResultet(
                faktiskPSB.arbeidstid.arbeidstakerList.first().arbeidstidInfo.perioder,
                forventetPSB.arbeidstid.arbeidstakerList.first().arbeidstidInfo.perioder
            )
        }
    }

    @Test
    fun `Gitt flere søknader på samme arbeidstaker konsumeres og persisteres, forvent riktige perioder ved sammenslåing`() {
        val org = "987654321"

        val psbSøknadInnholdHendelse1 = defaultPsbSøknadInnholdHendelse(
            journalpostId = "1",
            søkerAktørId = hovedSøkerAktørId,
            pleiepetrengendeAktørId = barn1AktørId,
            oppdateringsTidspunkt = ZonedDateTime.now(UTC),
            arbeidstid = Arbeidstid().medArbeidstaker(
                listOf(
                    defaultArbeidstaker(
                        organisasjonsnummer = org,
                        periode = Periode(LocalDate.parse("2021-08-01"), LocalDate.parse("2021-10-11")),
                        normaltTimerPerDag = 8,
                        faktiskArbeidTimerPerDag = 4
                    )
                )
            )
        )

        val psbSøknadInnholdHendelse2 = defaultPsbSøknadInnholdHendelse(
            journalpostId = "2",
            søkerAktørId = hovedSøkerAktørId,
            pleiepetrengendeAktørId = barn1AktørId,
            oppdateringsTidspunkt = ZonedDateTime.now(UTC).plusDays(1),
            arbeidstid = Arbeidstid().medArbeidstaker(
                listOf(
                    defaultArbeidstaker(
                        organisasjonsnummer = org,
                        periode = Periode(LocalDate.parse("2021-09-25"), LocalDate.parse("2021-12-01")),
                        normaltTimerPerDag = 8,
                        faktiskArbeidTimerPerDag = 2
                    )
                )
            )
        )
        k9SakProducer.leggPåTopic(psbSøknadInnholdHendelse1, K9_SAK_TOPIC)
        k9SakProducer.leggPåTopic(psbSøknadInnholdHendelse2, K9_SAK_TOPIC)

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val faktiskPSB =
                kotlin.runCatching {
                    søknadService.slåSammenSøknadsopplysningerPerBarn().first().søknad.getYtelse<PleiepengerSyktBarn>()
                }
                    .getOrNull()
            assertThat(faktiskPSB).isNotNull()
            assertThat(faktiskPSB!!.arbeidstid.arbeidstakerList.size).isEqualTo(1)

            logger.info("Antall søknader: {}", søknadRepository.findAll().size)
            assertResultet(
                faktiskArbeidstaker = faktiskPSB.arbeidstid.arbeidstakerList[0],
                forventetOrganisasjonsnummer = org,
                forventedePerioder = mapOf(
                    Periode(LocalDate.parse("2021-08-01"), LocalDate.parse("2021-09-24")) to ArbeidstidPeriodeInfo()
                        .medFaktiskArbeidTimerPerDag(Duration.ofHours(4))
                        .medJobberNormaltTimerPerDag(Duration.ofHours(8)),

                    Periode(LocalDate.parse("2021-09-25"), LocalDate.parse("2021-12-01")) to ArbeidstidPeriodeInfo()
                        .medFaktiskArbeidTimerPerDag(Duration.ofHours(2))
                        .medJobberNormaltTimerPerDag(Duration.ofHours(8))
                )
            )
        }
    }

    @Test
    fun `gitt konsumering og persistering av omsorg, forvent at søker har omsorg`() {

        // legg på 1 hendelse om omsorg fra k9-sak...
        val omsorgHendelse = defaultOmsorgHendelse(
            søkerAktørId = hovedSøkerAktørId,
            pleiepetrengendeAktørId = barn1AktørId,
            harOmsorgen = true
        )
        k9SakProducer.leggPåTopic(omsorgHendelse, K9_SAK_TOPIC)

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val faktiskOmsorg =
                kotlin.runCatching { omsorgService.hentOmsorg(hovedSøkerAktørId, barn1AktørId) }.getOrNull()

            assertNotNull(faktiskOmsorg)
            assertTrue(faktiskOmsorg!!.harOmsorgen)
        }
    }

    @Test
    fun `gitt konsumering og persistering der søker mister omsorg, forvent at søker ikke har omsorg`() {

        val omsorgHendelseMedOmsorg = defaultOmsorgHendelse(
            søkerAktørId = hovedSøkerAktørId,
            pleiepetrengendeAktørId = barn1AktørId,
            harOmsorgen = true
        )
        k9SakProducer.leggPåTopic(omsorgHendelseMedOmsorg, K9_SAK_TOPIC)

        val omsorgHendelseUtenOmsorg = defaultOmsorgHendelse(
            søkerAktørId = hovedSøkerAktørId,
            pleiepetrengendeAktørId = barn1AktørId,
            harOmsorgen = false
        )
        k9SakProducer.leggPåTopic(omsorgHendelseUtenOmsorg, K9_SAK_TOPIC)

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val faktiskOmsorg = omsorgService.harOmsorgen(hovedSøkerAktørId, barn1AktørId)
            assertNotNull(faktiskOmsorg)
            assertFalse(faktiskOmsorg)
        }
    }

    @Test
    fun `Gitt hendelse om trukket søknad, forvent at den slettes`() {
        val journalpostId = "1"

        val psbSøknadInnholdHendelse = defaultPsbSøknadInnholdHendelse(
            journalpostId = journalpostId,
            søkerAktørId = hovedSøkerAktørId,
            pleiepetrengendeAktørId = barn1AktørId,
            arbeidstid = Arbeidstid().medArbeidstaker(
                listOf(
                    defaultArbeidstaker(
                        organisasjonsnummer = "987654321",
                        periode = Periode(LocalDate.parse("2021-08-01"), LocalDate.parse("2021-10-11")),
                        normaltTimerPerDag = 8,
                        faktiskArbeidTimerPerDag = 4
                    )
                )
            )
        )
        k9SakProducer.leggPåTopic(psbSøknadInnholdHendelse, K9_SAK_TOPIC)

        await.atMost(Duration.ofSeconds(10)).ignoreExceptions().untilAsserted {
            val faktiskPSB =
                søknadService.slåSammenSøknadsopplysningerPerBarn().first().søknad.getYtelse<PleiepengerSyktBarn>()

            assertNotNull(faktiskPSB)
        }

        k9SakProducer.leggPåTopic(defaultSøknadTrukket(journalpostId = journalpostId), K9_SAK_TOPIC)
        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val søknad = søknadRepository.findById(journalpostId)
            assertTrue(søknad.isEmpty)
        }

    }
}
