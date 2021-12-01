package no.nav.sifinnsynapi.soknad

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.type.Periode
import no.nav.k9.søknad.ytelse.psb.v1.PleiepengerSyktBarn
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstid
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidInfo
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidPeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.tilsyn.TilsynPeriodeInfo
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.SøkerOppslagRespons
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.utils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.stream.Stream


@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
@ActiveProfiles("test")
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK) // Integrasjonstest - Kjører opp hele Spring Context med alle konfigurerte beans.
internal class SøknadServiceTest {

    @MockkBean
    private lateinit var søknadRepository: SøknadRepository

    @MockkBean
    private lateinit var oppslagsService: OppslagsService

    @Autowired
    private lateinit var søknadService: SøknadService

    private companion object {
        private val hovedSøkerAktørId = "11111111111"
        private val barn1AktørId = "22222222222"
        private val barn2AktørId = "33333333333"
    }

    @BeforeEach
    internal fun setUp() {
        every { oppslagsService.hentAktørId() } returns SøkerOppslagRespons(aktør_id = hovedSøkerAktørId)
        every { oppslagsService.hentBarn() } returns listOf(
            BarnOppslagDTO(aktør_id = barn1AktørId),
            BarnOppslagDTO(aktør_id = barn2AktørId)
        )
    }

    @Test
    fun `kan slå sammen perioder med tilsyn`() {
        every { søknadRepository.hentSøknaderSortertPåOppdatertTidspunkt(any()) } returns Stream.of(
            psbSøknadDAO(
                journalpostId = "1",
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
                    )
                )
            ),
            psbSøknadDAO(
                journalpostId = "2",
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
                    )
                ),
            )
        )

        val sammenSlåttTilsynsordning =
            søknadService.hentSøknadsopplysninger().getYtelse<PleiepengerSyktBarn>().tilsynsordning

        assertResultet(
            faktiskePerioder = sammenSlåttTilsynsordning.perioder,
            forventedePerioder = mapOf(
                Periode(LocalDate.parse("2021-08-01"), LocalDate.parse("2021-09-24")) to
                        TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(Duration.ofHours(4)),
                Periode(LocalDate.parse("2021-09-25"), LocalDate.parse("2021-12-01")) to
                        TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(Duration.ofHours(2).plusMinutes(30))
            )
        )
    }

    @Test
    fun `kan slå sammen arbeidstid for en arbeidstaker`() {
        val org = "987654321"
        every { søknadRepository.hentSøknaderSortertPåOppdatertTidspunkt(any()) } returns Stream.of(
            psbSøknadDAO(
                journalpostId = "1",
                søknad = defaultSøknad(
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
            ),
            psbSøknadDAO(
                journalpostId = "2",
                søknad = defaultSøknad(
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
                ),
            )
        )

        val sammenslåttArbeidstid = søknadService.hentSøknadsopplysninger().getYtelse<PleiepengerSyktBarn>().arbeidstid
        assertThat(sammenslåttArbeidstid.arbeidstakerList.size).isEqualTo(1)
        assertResultet(
            faktiskArbeidstaker = sammenslåttArbeidstid.arbeidstakerList.first(),
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

    @Test
    fun `kan slå sammen arbeidstid for flere arbeidstakere`() {
        val org1 = "911111111";
        val org2 = "922222222";
        val org3 = "933333333";
        val org4 = "944444444";
        every { søknadRepository.hentSøknaderSortertPåOppdatertTidspunkt(any()) } returns Stream.of(
            psbSøknadDAO(
                journalpostId = "1",
                søknad = defaultSøknad(
                    arbeidstid = Arbeidstid().medArbeidstaker(
                        listOf(
                            defaultArbeidstaker(
                                organisasjonsnummer = org1,
                                periode = Periode(LocalDate.parse("2021-08-01"), LocalDate.parse("2021-10-11")),
                                normaltTimerPerDag = 8,
                                faktiskArbeidTimerPerDag = 4
                            ),
                            defaultArbeidstaker(
                                organisasjonsnummer = org2,
                                periode = Periode(LocalDate.parse("2021-08-05"), LocalDate.parse("2021-09-12")),
                                normaltTimerPerDag = 8,
                                faktiskArbeidTimerPerDag = 4
                            ),
                            defaultArbeidstaker(
                                organisasjonsnummer = org3,
                                periode = Periode(LocalDate.parse("2021-06-01"), LocalDate.parse("2021-06-10")),
                                normaltTimerPerDag = 8,
                                faktiskArbeidTimerPerDag = 3
                            )
                        )
                    )
                )
            ),
            psbSøknadDAO(
                journalpostId = "2",
                søknad = defaultSøknad(
                    arbeidstid = Arbeidstid().medArbeidstaker(
                        listOf(
                            defaultArbeidstaker(
                                organisasjonsnummer = org1,
                                periode = Periode(LocalDate.parse("2021-09-25"), LocalDate.parse("2021-12-01")),
                                normaltTimerPerDag = 8,
                                faktiskArbeidTimerPerDag = 2
                            ),
                            defaultArbeidstaker(
                                organisasjonsnummer = org2,
                                periode = Periode(LocalDate.parse("2021-09-25"), LocalDate.parse("2021-12-01")),
                                normaltTimerPerDag = 8,
                                faktiskArbeidTimerPerDag = 2
                            ),
                            defaultArbeidstaker(
                                organisasjonsnummer = org4,
                                periode = Periode(LocalDate.parse("2021-11-13"), LocalDate.parse("2021-11-15")),
                                normaltTimerPerDag = 8,
                                faktiskArbeidTimerPerDag = 2
                            )
                        )
                    )
                ),
            )
        )

        val sammenslåttYtelse = søknadService.hentSøknadsopplysninger().getYtelse<PleiepengerSyktBarn>()
        val resultatArbeidstakere = sortertArbeidstakere(sammenslåttYtelse)
        assertThat(resultatArbeidstakere.size).isEqualTo(4)

        assertResultet(
            faktiskArbeidstaker = resultatArbeidstakere[0],
            forventetOrganisasjonsnummer = org1,
            forventedePerioder = mapOf(
                Periode(LocalDate.parse("2021-08-01"), LocalDate.parse("2021-09-24")) to ArbeidstidPeriodeInfo()
                    .medFaktiskArbeidTimerPerDag(Duration.ofHours(4))
                    .medJobberNormaltTimerPerDag(Duration.ofHours(8)),
                Periode(LocalDate.parse("2021-09-25"), LocalDate.parse("2021-12-01")) to ArbeidstidPeriodeInfo()
                    .medFaktiskArbeidTimerPerDag(Duration.ofHours(2))
                    .medJobberNormaltTimerPerDag(Duration.ofHours(8))
            )
        )

        assertResultet(
            faktiskArbeidstaker = resultatArbeidstakere[1],
            forventetOrganisasjonsnummer = org2,
            forventedePerioder = mapOf(
                Periode(LocalDate.parse("2021-08-05"), LocalDate.parse("2021-09-12")) to ArbeidstidPeriodeInfo()
                    .medFaktiskArbeidTimerPerDag(Duration.ofHours(4))
                    .medJobberNormaltTimerPerDag(Duration.ofHours(8)),
                Periode(LocalDate.parse("2021-09-25"), LocalDate.parse("2021-12-01")) to ArbeidstidPeriodeInfo()
                    .medFaktiskArbeidTimerPerDag(Duration.ofHours(2))
                    .medJobberNormaltTimerPerDag(Duration.ofHours(8))
            )
        )

        assertResultet(
            faktiskArbeidstaker = resultatArbeidstakere[2],
            forventetOrganisasjonsnummer = org3,
            forventedePerioder = mapOf(
                Periode(LocalDate.parse("2021-06-01"), LocalDate.parse("2021-06-10")) to ArbeidstidPeriodeInfo()
                    .medFaktiskArbeidTimerPerDag(Duration.ofHours(3))
                    .medJobberNormaltTimerPerDag(Duration.ofHours(8))
            )
        )

        assertResultet(
            faktiskArbeidstaker = resultatArbeidstakere[3],
            forventetOrganisasjonsnummer = org4,
            forventedePerioder = mapOf(
                Periode(LocalDate.parse("2021-11-13"), LocalDate.parse("2021-11-15")) to ArbeidstidPeriodeInfo()
                    .medFaktiskArbeidTimerPerDag(Duration.ofHours(2))
                    .medJobberNormaltTimerPerDag(Duration.ofHours(8))
            )
        )
    }

    @Test
    fun `kan slå sammen arbeidstid for frilanser`() {
        every { søknadRepository.hentSøknaderSortertPåOppdatertTidspunkt(any()) } returns Stream.of(
            psbSøknadDAO(
                journalpostId = "1",
                søknad = defaultSøknad(
                    arbeidstid = Arbeidstid().medFrilanserArbeidstid(
                        ArbeidstidInfo().medPerioder(
                            mapOf(
                                Periode(LocalDate.parse("2021-08-01"), LocalDate.parse("2021-10-11")) to
                                        ArbeidstidPeriodeInfo()
                                            .medJobberNormaltTimerPerDag(Duration.ofHours(8))
                                            .medFaktiskArbeidTimerPerDag(Duration.ofHours(4))
                            )
                        )
                    )
                )
            ),
            psbSøknadDAO(
                journalpostId = "2",
                søknad = defaultSøknad(
                    arbeidstid = Arbeidstid().medFrilanserArbeidstid(
                        ArbeidstidInfo().medPerioder(
                            mapOf(
                                Periode(LocalDate.parse("2021-09-25"), LocalDate.parse("2021-12-01")) to
                                        ArbeidstidPeriodeInfo()
                                            .medJobberNormaltTimerPerDag(Duration.ofHours(8))
                                            .medFaktiskArbeidTimerPerDag(Duration.ofHours(2))
                            )
                        )
                    )
                ),
            )
        )

        val sammenslåttArbeidstid = søknadService.hentSøknadsopplysninger().getYtelse<PleiepengerSyktBarn>().arbeidstid
        assertResultet(
            faktiskePerioder = sammenslåttArbeidstid.frilanserArbeidstidInfo.get().perioder,
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

    @Test
    fun `kan slå sammen arbeidstid for selvstendig næringsdrivende`() {
        every { søknadRepository.hentSøknaderSortertPåOppdatertTidspunkt(any()) } returns Stream.of(
            psbSøknadDAO(
                journalpostId = "1",
                søknad = defaultSøknad(
                    arbeidstid = Arbeidstid().medSelvstendigNæringsdrivendeArbeidstidInfo(
                        ArbeidstidInfo().medPerioder(
                            mapOf(
                                Periode(LocalDate.parse("2021-08-01"), LocalDate.parse("2021-10-11")) to
                                        ArbeidstidPeriodeInfo()
                                            .medJobberNormaltTimerPerDag(Duration.ofHours(8))
                                            .medFaktiskArbeidTimerPerDag(Duration.ofHours(4))
                            )
                        )
                    )
                )
            ),
            psbSøknadDAO(
                journalpostId = "2",
                søknad = defaultSøknad(
                    arbeidstid = Arbeidstid().medSelvstendigNæringsdrivendeArbeidstidInfo(
                        ArbeidstidInfo().medPerioder(
                            mapOf(
                                Periode(LocalDate.parse("2021-09-25"), LocalDate.parse("2021-12-01")) to
                                        ArbeidstidPeriodeInfo()
                                            .medJobberNormaltTimerPerDag(Duration.ofHours(8))
                                            .medFaktiskArbeidTimerPerDag(Duration.ofHours(2))
                            )
                        )
                    )
                ),
            )
        )

        val sammenslåttArbeidstid = søknadService.hentSøknadsopplysninger().getYtelse<PleiepengerSyktBarn>().arbeidstid
        assertResultet(
            faktiskePerioder = sammenslåttArbeidstid.selvstendigNæringsdrivendeArbeidstidInfo.get().perioder,
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
