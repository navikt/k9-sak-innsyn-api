package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.OrganisasjonOppslagDTO
import no.nav.sifinnsynapi.soknad.SøknadDTO
import no.nav.sifinnsynapi.utils.defaultSøknad
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.type.Organisasjonsnummer
import no.nav.k9.søknad.felles.type.Periode
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstaker
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstid
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidInfo
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidPeriodeInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import java.time.LocalDate

class SakForTilgangTest {

    @Test
    fun `mapper søknadsperioder og orgnr fra k9-format`() {
        val sak = innsynSøknad(
            defaultSøknad(søknadsPeriode = Periode(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-01-01")))
        ).tilSakForTilgang()

        assertEquals(
            SakForTilgang(
                søknadsperioder = listOf(Datoperiode(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-01-01"))),
                arbeidsgiverOrgnr = setOf("999999999"),
                harArbeidstidSomSelvstendigNæringsdrivende = false,
                barnHarIdentitetsnummer = true,
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

        val sak = innsynSøknad(defaultSøknad(arbeidstid = arbeidstid)).tilSakForTilgang()

        assertEquals(emptySet<String>(), sak.arbeidsgiverOrgnr)
    }

    @Test
    fun `arbeidstaker uten normalarbeidstid over null utelates`() {
        val arbeidstid = Arbeidstid().medArbeidstaker(
            listOf(
                Arbeidstaker()
                    .medOrganisasjonsnummer(Organisasjonsnummer.of("111111111"))
                    .medArbeidstidInfo(arbeidstidInfo(Duration.ZERO)),
                Arbeidstaker()
                    .medOrganisasjonsnummer(Organisasjonsnummer.of("222222222"))
                    .medArbeidstidInfo(arbeidstidInfo(Duration.ofHours(8))),
            )
        )

        val sak = innsynSøknad(defaultSøknad(arbeidstid = arbeidstid)).tilSakForTilgang()

        assertEquals(setOf("222222222"), sak.arbeidsgiverOrgnr)
    }

    @ParameterizedTest(name = "identitetsnummer=[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = [" "])
    fun `barn uten identitetsnummer mappes til barnHarIdentitetsnummer false`(identitetsnummer: String?) {
        val sak = innsynSøknad(defaultSøknad(), identitetsnummer = identitetsnummer).tilSakForTilgang()

        assertFalse(sak.barnHarIdentitetsnummer)
    }

    @Test
    fun `SN med normalarbeidstid over null gir SN-arbeidstid`() {
        val arbeidstid = Arbeidstid().medSelvstendigNæringsdrivendeArbeidstidInfo(
            arbeidstidInfo(Duration.ofMinutes(450))
        )

        val sak = innsynSøknad(defaultSøknad(arbeidstid = arbeidstid)).tilSakForTilgang()

        assertTrue(sak.harArbeidstidSomSelvstendigNæringsdrivende)
    }

    @Test
    fun `SN uten normalarbeidstid over null gir ikke SN-arbeidstid, som i frontend`() {
        val arbeidstid = Arbeidstid().medSelvstendigNæringsdrivendeArbeidstidInfo(arbeidstidInfo(Duration.ZERO))

        val sak = innsynSøknad(defaultSøknad(arbeidstid = arbeidstid)).tilSakForTilgang()

        assertFalse(sak.harArbeidstidSomSelvstendigNæringsdrivende)
    }

    @Test
    fun `SN uten perioder gir ikke SN-arbeidstid, som i frontend`() {
        val arbeidstid = Arbeidstid().medSelvstendigNæringsdrivendeArbeidstidInfo(ArbeidstidInfo())

        val sak = innsynSøknad(defaultSøknad(arbeidstid = arbeidstid)).tilSakForTilgang()

        assertFalse(sak.harArbeidstidSomSelvstendigNæringsdrivende)
    }

    @Test
    fun `grupperer ansettelsesperioder per orgnr`() {
        val gruppert = listOf(
            OrganisasjonOppslagDTO("111", null, LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-02")),
            OrganisasjonOppslagDTO("222", null, null, null),
            OrganisasjonOppslagDTO("111", null, LocalDate.parse("2024-01-04"), null),
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

    private fun innsynSøknad(
        søknad: no.nav.k9.søknad.Søknad,
        identitetsnummer: String? = "02119970079",
    ) = SøknadDTO(
        barn = BarnOppslagDTO(
            fødselsdato = LocalDate.parse("2020-01-01"),
            fornavn = "BARN",
            etternavn = "BARNESEN",
            aktørId = "1000000000001",
            identitetsnummer = identitetsnummer,
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
