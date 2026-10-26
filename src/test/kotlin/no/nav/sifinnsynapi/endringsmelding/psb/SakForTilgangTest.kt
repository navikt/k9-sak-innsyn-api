package no.nav.sifinnsynapi.endringsmelding.psb

import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.type.Periode
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstaker
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstid
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidInfo
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidPeriodeInfo
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.OrganisasjonOppslagDTO
import no.nav.sifinnsynapi.soknad.SøknadDTO
import no.nav.sifinnsynapi.utils.defaultSøknad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import java.time.LocalDate

class SakForTilgangTest {

    private val søknadsperiode = Periode(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-01-01"))

    @Test
    fun `mapper søknadsperioder og orgnr fra k9-format`() {
        val sak = søknadDTO(defaultSøknad(søknadsPeriode = søknadsperiode)).tilSakForTilgang()

        assertEquals(
            SakForTilgang(
                søknadsperioder = listOf(Datoperiode(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-01-01"))),
                arbeidsgiverOrgnr = setOf("999999999"),
                harSelvstendigNæringsdrivendePerioder = false,
            ),
            sak
        )
    }

    @Test
    fun `arbeidstaker uten orgnr utelates`() {
        val arbeidstid = Arbeidstid().medArbeidstaker(
            listOf(
                Arbeidstaker()
                    .medNorskIdentitetsnummer(NorskIdentitetsnummer.of("12345678910"))
                    .medArbeidstidInfo(arbeidstidInfo(Duration.ofHours(8)))
            )
        )

        val sak = søknadDTO(defaultSøknad(søknadsPeriode = søknadsperiode, arbeidstid = arbeidstid)).tilSakForTilgang()

        assertEquals(emptySet<String>(), sak.arbeidsgiverOrgnr)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["PT7H30M", "PT0S"])
    fun `SN med perioder gir SN-perioder uansett timetall`(jobberNormaltTimerPerDag: String) {
        val arbeidstid = Arbeidstid().medSelvstendigNæringsdrivendeArbeidstidInfo(
            arbeidstidInfo(Duration.parse(jobberNormaltTimerPerDag))
        )

        val sak = søknadDTO(defaultSøknad(søknadsPeriode = søknadsperiode, arbeidstid = arbeidstid)).tilSakForTilgang()

        assertTrue(sak.harSelvstendigNæringsdrivendePerioder)
    }

    @Test
    fun `frilans gir ikke SN-perioder`() {
        val arbeidstid = Arbeidstid().medFrilanserArbeidstid(arbeidstidInfo(Duration.ofMinutes(450)))

        val sak = søknadDTO(defaultSøknad(søknadsPeriode = søknadsperiode, arbeidstid = arbeidstid)).tilSakForTilgang()

        assertFalse(sak.harSelvstendigNæringsdrivendePerioder)
    }

    @Test
    fun `grupperer ansettelsesperioder per orgnr`() {
        val gruppert = listOf(
            OrganisasjonOppslagDTO("111", LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-02")),
            OrganisasjonOppslagDTO("222", null, null),
            OrganisasjonOppslagDTO("111", LocalDate.parse("2024-01-04"), null),
        ).grupperPerArbeidsgiver()

        assertEquals(
            listOf(
                ArbeidsgiverForTilgang(
                    "111",
                    listOf(
                        Ansettelsesperiode(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-02")),
                        Ansettelsesperiode(LocalDate.parse("2024-01-04"), null),
                    )
                ),
                ArbeidsgiverForTilgang("222", listOf(Ansettelsesperiode(null, null))),
            ),
            gruppert
        )
    }

    private fun søknadDTO(søknad: Søknad) = SøknadDTO(
        barn = BarnOppslagDTO(
            fødselsdato = LocalDate.parse("2020-01-01"),
            fornavn = "BARN",
            etternavn = "BARNESEN",
            aktørId = "1000000000001",
        ),
        søknad = søknad,
    )

    private fun arbeidstidInfo(jobberNormaltTimerPerDag: Duration) = ArbeidstidInfo().medPerioder(
        mapOf(
            Periode(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-31")) to
                ArbeidstidPeriodeInfo()
                    .medJobberNormaltTimerPerDag(jobberNormaltTimerPerDag)
                    .medFaktiskArbeidTimerPerDag(Duration.ZERO)
        )
    )
}
