package no.nav.sifinnsynapi.util

import no.nav.k9.søknad.felles.personopplysninger.Barn
import no.nav.k9.søknad.felles.personopplysninger.Bosteder
import no.nav.k9.søknad.felles.personopplysninger.Bosteder.BostedPeriodeInfo
import no.nav.k9.søknad.felles.personopplysninger.Utenlandsopphold
import no.nav.k9.søknad.felles.personopplysninger.Utenlandsopphold.UtenlandsoppholdPeriodeInfo
import no.nav.k9.søknad.felles.type.Landkode
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.type.Organisasjonsnummer
import no.nav.k9.søknad.felles.type.Periode
import no.nav.k9.søknad.ytelse.psb.v1.*
import no.nav.k9.søknad.ytelse.psb.v1.Beredskap.BeredskapPeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.LovbestemtFerie.LovbestemtFeriePeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.Nattevåk.NattevåkPeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstaker
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstid
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidInfo
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidPeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.tilsyn.TilsynPeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.tilsyn.Tilsynsordning
import java.time.Duration
import java.time.LocalDate


fun komplettYtelse(periode: Periode): PleiepengerSyktBarn {
    return komplettYtelse(listOf(periode))
}

fun komplettYtelse(periodeList: List<Periode>): PleiepengerSyktBarn {
    val barn = Barn().medNorskIdentitetsnummer(NorskIdentitetsnummer.of("22222222222"))
    val omsorg = Omsorg().medRelasjonTilBarnet(Omsorg.BarnRelasjon.MOR)
    val søknadInfo = DataBruktTilUtledning(true, true, false, false, true)
    val tilsynsordning = lagTilsynsordning()
    val arbeidstaker = lagArbeidstaker(periodeList)
    val uttak = lagUttak(periodeList)
    val arbeidstid = Arbeidstid().medArbeidstaker(listOf(arbeidstaker))

    return PleiepengerSyktBarn()
        .medSøknadsperiode(periodeList)
        .medSøknadInfo(søknadInfo)
        .medBarn(barn)
        .medTilsynsordning(tilsynsordning)
        .medArbeidstid(arbeidstid)
        .medUttak(uttak)
        .medOmsorg(omsorg)
}

fun lagTilsynsordning(): Tilsynsordning {
    return Tilsynsordning().medPerioder(
        mapOf(
            Periode(
                LocalDate.parse("2021-08-12"),
                LocalDate.parse("2021-08-13")
            ) to TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(
                Duration.ofHours(2)
            ),
            Periode(
                LocalDate.parse("2021-09-01"),
                LocalDate.parse("2021-09-15")
            ) to TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(
                Duration.ofHours(7).plusMinutes(30)
            ),
            Periode(
                LocalDate.parse("2021-10-01"),
                LocalDate.parse("2021-10-15")
            ) to TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(
                Duration.ofHours(4)
            ),
            Periode(
                LocalDate.parse("2021-11-01"),
                LocalDate.parse("2021-11-15")
            ) to TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(
                Duration.ofHours(7).plusMinutes(30)
            )
        )
    )
}

fun lagArbeidstaker(periodeList: List<Periode>): Arbeidstaker {
    val arbeidstidPeriodeInfo =
        ArbeidstidPeriodeInfo()
            .medJobberNormaltTimerPerDag(Duration.ofHours(7).plusMinutes(30))
            .medFaktiskArbeidTimerPerDag(Duration.ofHours(3))
    return Arbeidstaker()
        .medOrganisasjonsnummer(Organisasjonsnummer.of("999999999"))
        .medArbeidstidInfo(
            ArbeidstidInfo()
                .medPerioder(lagPerioder(periodeList, arbeidstidPeriodeInfo))
        )
}

fun lagUttak(periodeList: List<Periode>): Uttak {
    val uttakPeriodeInfo = Uttak.UttakPeriodeInfo(Duration.ofHours(7).plusMinutes(30))
    return Uttak().medPerioder(lagPerioder(periodeList, uttakPeriodeInfo))
}

fun <T> lagPerioder(periodeList: List<Periode>, periodeInfo: T): HashMap<Periode, T> {
    val resultatMap = HashMap<Periode, T>()
    for (periode in periodeList) {
        resultatMap[periode] = periodeInfo
    }
    return resultatMap
}
