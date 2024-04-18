package no.nav.sifinnsynapi.soknad

import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.size
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.personopplysninger.Søker
import no.nav.k9.søknad.felles.type.Periode
import no.nav.k9.søknad.ytelse.psb.v1.PleiepengerSyktBarn
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstid
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidPeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.tilsyn.TilsynPeriodeInfo
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.common.AktørId
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.LegacyInnsynApiService
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.LegacySøknadDTO
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.LegacySøknadNotFoundException
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.LegacySøknadstype
import no.nav.sifinnsynapi.omsorg.OmsorgDAO
import no.nav.sifinnsynapi.omsorg.OmsorgRepository
import no.nav.sifinnsynapi.oppslag.*
import no.nav.sifinnsynapi.utils.*
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.*


@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
@ActiveProfiles("test")
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK) // Integrasjonstest - Kjører opp hele Spring Context med alle konfigurerte beans.
internal class SøknadServiceTest {

    @Autowired
    private lateinit var søknadRepository: SøknadRepository

    @Autowired
    private lateinit var søknadService: SøknadService

    @Autowired
    private lateinit var omsorgRepository: OmsorgRepository

    @MockkBean(relaxed = true)
    private lateinit var oppslagsService: OppslagsService

    @MockkBean
    private lateinit var legacyInnsynApiService: LegacyInnsynApiService

    private companion object {
        private val annenSøkerAktørId = "00000000000"
        private val hovedSøkerAktørId = "11111111111"
        private val barn1AktørId = "22222222222"
        private val barn2AktørId = "33333333333"
    }

    @BeforeAll
    fun initial() {
        søknadRepository.saveAll(
            listOf(
                psbSøknadDAO(
                    journalpostId = "1",
                    søkerAktørId = hovedSøkerAktørId,
                    pleietrengendeAktørId = barn1AktørId,
                    søknad = defaultSøknad(
                        tilsynsordning = defaultTilsynsordning(
                            mapOf(
                                Periode(
                                    LocalDate.parse("2021-08-01"),
                                    LocalDate.parse("2021-10-11")
                                ) to TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(
                                    Duration.ofHours(4)
                                )
                            )
                        ),
                        mottattDato = ZonedDateTime.now()
                    )
                ),
                psbSøknadDAO(
                    journalpostId = "2",
                    søkerAktørId = hovedSøkerAktørId,
                    pleietrengendeAktørId = barn1AktørId,
                    søknad = defaultSøknad(
                        tilsynsordning = defaultTilsynsordning(
                            mapOf(
                                Periode(
                                    LocalDate.parse("2021-09-25"),
                                    LocalDate.parse("2021-12-01")
                                ) to TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(
                                    Duration.ofHours(2).plusMinutes(30)
                                )
                            )
                        ),
                        mottattDato = ZonedDateTime.now()
                    )
                )
            )
        )
    }

    @BeforeEach
    fun setUp() {
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
                    harOmsorgen = false,
                    opprettetDato = ZonedDateTime.now(UTC),
                    oppdatertDato = ZonedDateTime.now(UTC)
                ),
                OmsorgDAO(
                    id = "2",
                    søkerAktørId = hovedSøkerAktørId,
                    pleietrengendeAktørId = barn2AktørId,
                    harOmsorgen = false,
                    opprettetDato = ZonedDateTime.now(UTC),
                    oppdatertDato = ZonedDateTime.now(UTC)
                )
            )
        )
    }

    @AfterEach
    fun tearDown() {
        omsorgRepository.deleteAll()
    }

    @AfterAll
    fun cleanup() {
        søknadRepository.deleteAll()
        omsorgRepository.deleteAll()
    }

    @Test
    fun `gitt søknader med tilsyn og arbeidstid fra flere søkere på samme barn, forvent kun at tilsynsperioder blir slått sammen`() {
        val organisasjonsnummer = "987654321"

        // gitt at det eksiterer to søknader med arbeidstid og omsorgstilbud fra 2 søkere på samme barn...
        søknadRepository.saveAll(
            listOf(
                psbSøknadDAO(
                    journalpostId = "1",
                    søkerAktørId = hovedSøkerAktørId,
                    pleietrengendeAktørId = barn1AktørId,
                    søknad = defaultSøknad(
                        arbeidstid = Arbeidstid().medArbeidstaker(
                            listOf(
                                defaultArbeidstaker(
                                    organisasjonsnummer = organisasjonsnummer,
                                    periode = Periode(LocalDate.parse("2021-08-01"), LocalDate.parse("2021-10-11")),
                                    normaltTimerPerDag = 8,
                                    faktiskArbeidTimerPerDag = 4
                                )
                            )
                        ),
                        tilsynsordning = defaultTilsynsordning(
                            mapOf(
                                Periode(
                                    LocalDate.parse("2021-08-01"),
                                    LocalDate.parse("2021-10-11")
                                ) to TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(
                                    Duration.ofHours(4)
                                )
                            )
                        ),
                        mottattDato = ZonedDateTime.now()
                    )
                ),
                psbSøknadDAO(
                    journalpostId = "2",
                    søkerAktørId = annenSøkerAktørId,
                    pleietrengendeAktørId = barn1AktørId,
                    søknad = defaultSøknad(
                        arbeidstid = Arbeidstid().medArbeidstaker(
                            listOf(
                                defaultArbeidstaker(
                                    organisasjonsnummer = "933333333",
                                    periode = Periode(LocalDate.parse("2021-09-25"), LocalDate.parse("2021-12-01")),
                                    normaltTimerPerDag = 8,
                                    faktiskArbeidTimerPerDag = 2
                                )
                            )
                        ),
                        tilsynsordning = defaultTilsynsordning(
                            mapOf(
                                Periode(
                                    LocalDate.parse("2021-09-25"),
                                    LocalDate.parse("2021-12-01")
                                ) to TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(
                                    Duration.ofHours(2).plusMinutes(30)
                                )
                            )
                        ),
                        mottattDato = ZonedDateTime.now()
                    )
                )
            )
        )

        // forvent at søknadene blir slått sammen.
        val søknad: Søknad? = søknadService.slåSammenSøknaderFor(hovedSøkerAktørId, barn1AktørId)
        Assert.assertNotNull(søknad)

        val ytelse = søknad!!.getYtelse<PleiepengerSyktBarn>()
        val sammenslåttTilsynsordning = ytelse.tilsynsordning
        val sammenslåttArbeidstid = ytelse.arbeidstid

        // forvent det kun er en arbeidstaker i den sammenslåtte søknaden...
        assertThat(sammenslåttArbeidstid.arbeidstakerList.size).isEqualTo(1)

        // og at periodene for arbeidstid kun tilhørerer søker som har gjort oppslaget.
        assertResultet(
            faktiskArbeidstaker = sammenslåttArbeidstid.arbeidstakerList.first(),
            forventetOrganisasjonsnummer = organisasjonsnummer,
            forventedePerioder = mapOf(
                Periode(LocalDate.parse("2021-08-01"), LocalDate.parse("2021-10-11")) to ArbeidstidPeriodeInfo()
                    .medFaktiskArbeidTimerPerDag(Duration.ofHours(4))
                    .medJobberNormaltTimerPerDag(Duration.ofHours(8))
            )
        )

        // Samt, at periodene med omsorgstilbd fra de to søknadene med forskjellige søkere er slått sammen.
        assertResultet(
            faktiskePerioder = sammenslåttTilsynsordning.perioder,
            forventedePerioder = mapOf(
                Periode(LocalDate.parse("2021-08-01"), LocalDate.parse("2021-09-24")) to
                        TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(Duration.ofHours(4)),
                Periode(LocalDate.parse("2021-09-25"), LocalDate.parse("2021-12-01")) to
                        TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(Duration.ofHours(2).plusMinutes(30))
            )
        )
    }

    @Test
    fun `gitt at søker ikke har barn, forvent tom liste`() {
        every { oppslagsService.hentBarn() } answers { listOf() }
        assertThat(søknadService.slåSammenSøknadsopplysningerPerBarn()).isEmpty()
    }

    @Test
    //@Disabled("Har deaktivert sjekk på omsorg i SøknadService.kt:28")
    fun `gitt at søker ikke har omsorg for barna, forvent tom liste`() {
        omsorgRepository.oppdaterOmsorg(false, hovedSøkerAktørId, barn1AktørId)
        omsorgRepository.oppdaterOmsorg(false, hovedSøkerAktørId, barn2AktørId)

        assertFalse(
            omsorgRepository.findBySøkerAktørIdAndPleietrengendeAktørId(
                hovedSøkerAktørId,
                barn1AktørId
            )!!.harOmsorgen
        )
        assertFalse(
            omsorgRepository.findBySøkerAktørIdAndPleietrengendeAktørId(
                hovedSøkerAktørId,
                barn2AktørId
            )!!.harOmsorgen
        )

        assertThat(søknadService.slåSammenSøknadsopplysningerPerBarn()).isEmpty()

    }

    @Test
    fun `gitt at søker kun har omsorg for et av barna, forvent 1 sammenslått søknad`() {
        omsorgRepository.oppdaterOmsorg(true, hovedSøkerAktørId, barn1AktørId)
        omsorgRepository.oppdaterOmsorg(false, hovedSøkerAktørId, barn2AktørId)

        assertThat(søknadService.slåSammenSøknadsopplysningerPerBarn()).size().isEqualTo(1)
    }

    @Test
    fun hentArbeidsgiverMeldingFil() {
        val organisasjonsnummer = "917755645"

        val søknadId = UUID.randomUUID()
        every { legacyInnsynApiService.hentLegacySøknad(any()) } returns LegacySøknadDTO(
            søknadId = søknadId,
            journalpostId = "123456789",
            saksId = "abc123",
            søknadstype = LegacySøknadstype.PP_SYKT_BARN,
            søknad = JSONObject(
                // language=json
                """
                {
                    "fraOgMed": "2021-01-01",
                    "tilOgMed": "2021-01-01",
                    "søker": {
                      "mellomnavn": "Mellomnavn",
                      "etternavn": "Nordmann",
                      "aktørId": "123456",
                      "fødselsnummer": "02119970078",
                      "fornavn": "Ola"
                    },
                    "arbeidsgivere": {
                      "organisasjoner": [
                        {
                          "navn": "Stolt Hane",
                          "organisasjonsnummer": "917755736"
                        },
                        {
                          "navn": "Snill Torpedo",
                          "organisasjonsnummer": "$organisasjonsnummer"
                        },
                        {
                          "navn": "Something Fishy",
                          "organisasjonsnummer": "917755645"
                        }
                      ]
                    }
                }
                """.trimIndent()
            ).toMap()
        )

        await.atMost(Duration.ofSeconds(10)).ignoreException(LegacySøknadNotFoundException::class.java).untilAsserted {
            val bytes = søknadService.hentArbeidsgiverMeldingFil(søknadId, organisasjonsnummer)
            Assertions.assertNotNull(bytes)
            assertk.assertThat(bytes).isNotEmpty()
            assertk.assertThat(bytes).size().isGreaterThan(1000)
        }
    }

    @Test
    fun `Finner organisasjon selvom arbeidsgivere ligger som JSONArray`() {
        val organisasjonsnummer = "917755645"

        val søknadId = UUID.randomUUID()
        every { legacyInnsynApiService.hentLegacySøknad(any()) } returns LegacySøknadDTO(
            søknadId = søknadId,
            journalpostId = "123456789",
            saksId = "abc123",
            søknadstype = LegacySøknadstype.PP_SYKT_BARN,
            søknad = JSONObject(
                // language=json
                """
                {
                    "fraOgMed": "2021-01-01",
                    "tilOgMed": "2021-01-01",
                    "søker": {
                      "mellomnavn": "Mellomnavn",
                      "etternavn": "Nordmann",
                      "aktørId": "123456",
                      "fødselsnummer": "02119970078",
                      "fornavn": "Ola"
                    },
                    "arbeidsgivere": [
                    {
                      "erAnsatt": true,
                      "navn": "Peppes",
                      "arbeidsforhold": null,
                      "organisasjonsnummer": "917755645"
                    },
                    {
                      "erAnsatt": true,
                      "navn": "Mix",
                      "arbeidsforhold": null,
                      "organisasjonsnummer": "896967762"
                    }
                  ]
                }
                """.trimIndent()
            ).toMap()
        )

        await.atMost(Duration.ofSeconds(10)).ignoreException(LegacySøknadNotFoundException::class.java).untilAsserted {
            val bytes = søknadService.hentArbeidsgiverMeldingFil(søknadId, organisasjonsnummer)
            Assertions.assertNotNull(bytes)
            assertk.assertThat(bytes).isNotEmpty()
            assertk.assertThat(bytes).size().isGreaterThan(1000)
        }
    }

    private fun psbSøknadDAO(
        journalpostId: String,
        søkerAktørId: String = hovedSøkerAktørId,
        pleietrengendeAktørId: String = "22222222222",
        søknad: Søknad
    ) = PsbSøknadDAO(
        journalpostId = journalpostId,
        søkerAktørId = søkerAktørId,
        pleietrengendeAktørId = pleietrengendeAktørId,
        søknad = søknad.somJson(),
        opprettetDato = ZonedDateTime.now(UTC),
        oppdatertDato = ZonedDateTime.now(UTC).minusWeeks(2)
    )
}
