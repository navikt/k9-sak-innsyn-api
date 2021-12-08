package no.nav.sifinnsynapi.soknad

import assertk.assertions.isEqualTo
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
import no.nav.sifinnsynapi.konsument.k9sak.OnpremKafkaHendelseKonsumentIntegrasjonsTest
import no.nav.sifinnsynapi.omsorg.OmsorgDAO
import no.nav.sifinnsynapi.omsorg.OmsorgRepository
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.oppslag.SøkerOppslagRespons
import no.nav.sifinnsynapi.utils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.jupiter.api.BeforeAll
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

    @Autowired
    private lateinit var søknadRepository: SøknadRepository

    @Autowired
    private lateinit var søknadService: SøknadService

    private companion object {
        private val annenSøkerAktørId = "00000000000"
        private val hovedSøkerAktørId = "11111111111"
        private val barn1AktørId = "22222222222"
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
                        )
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
                        )
                    )
                )
            )
        )

        // forent at søknadene blir slått sammen...
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
