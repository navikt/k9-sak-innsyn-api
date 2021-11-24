package no.nav.sifinnsynapi.soknadLeggerimport assertk.assertions.isEqualTo
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.type.Organisasjonsnummer
import no.nav.k9.søknad.felles.type.Periode
import no.nav.k9.søknad.ytelse.psb.v1.PleiepengerSyktBarn
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstaker
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstid
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidPeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.tilsyn.TilsynPeriodeInfo
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.oppslag.OppslagRespons
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.utils.defaultArbeidstaker
import no.nav.sifinnsynapi.utils.defaultSøknad
import no.nav.sifinnsynapi.utils.defaultTilsynsordning
import no.nav.sifinnsynapi.utils.somJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertNotNull
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
    }

    @BeforeEach
    internal fun setUp() {
        every { oppslagsService.hentAktørId() } returns OppslagRespons(aktør_id = hovedSøkerAktørId)
    }

    @Test
    fun `kan slå sammen perioder med tilsyn`() {

        every { søknadRepository.hentSøknaderSortertPåOppdatertTidspunkt(any(), any()) } returns Stream.of(
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
    fun `kan slå sammen data for ett arbeidsforhold`() {
        val ORGANISASJONSNUMMER = "987654321";
        every { søknadRepository.hentSøknaderSortertPåOppdatertTidspunkt(any(), any()) } returns Stream.of(
            psbSøknadDAO(
                journalpostId = "1",
                søknad = defaultSøknad(
                    arbeidstid = Arbeidstid().medArbeidstaker(
                        listOf(
                            defaultArbeidstaker(
                                organisasjonsnummer = ORGANISASJONSNUMMER,
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
                                organisasjonsnummer = ORGANISASJONSNUMMER,
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
            forventetOrganisasjonsnummer = ORGANISASJONSNUMMER,
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

    private fun <T> assertResultet(faktiskePerioder: Map<Periode, T>, forventedePerioder: Map<Periode, T>) {
        assertThat(faktiskePerioder.size).isEqualTo(forventedePerioder.size)
        forventedePerioder.forEach { forventetPeriode: Map.Entry<Periode, T> ->
            val data = faktiskePerioder[forventetPeriode.key]
            assertNotNull(data)
            assertk.assertThat(data).isEqualTo(forventetPeriode.value)
            assertThat(data).isEqualTo(forventetPeriode.value)
        }
    }

    private fun assertResultet(
        faktiskArbeidstaker: Arbeidstaker,
        forventetOrganisasjonsnummer: String,
        forventedePerioder: Map<Periode, ArbeidstidPeriodeInfo>
    ) {
        assertThat(faktiskArbeidstaker.organisasjonsnummer).isEqualTo(
            Organisasjonsnummer.of(forventetOrganisasjonsnummer)
        )
        assertResultet(faktiskArbeidstaker.arbeidstidInfo.perioder, forventedePerioder)
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
