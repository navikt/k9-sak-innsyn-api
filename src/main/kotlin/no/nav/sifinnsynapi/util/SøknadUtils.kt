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


fun komplettYtelse(periode: Periode): PleiepengerSyktBarn {
    return komplettYtelse(listOf(periode))
}

fun komplettYtelse(periodeList: List<Periode>): PleiepengerSyktBarn {
    val barn = Barn(NorskIdentitetsnummer.of("22211111111"), null)
    val omsorg = Omsorg().medRelasjonTilBarnet(Omsorg.BarnRelasjon.MOR)
    val søknadInfo = DataBruktTilUtledning(
        true, true,
        false, false, true
    )
    val infoFraPunsj = InfoFraPunsj()
        .medSøknadenInneholderInfomasjonSomIkkeKanPunsjes(false)
    val uttak = lagUttak(periodeList)
    val nattevåk = lagNattevåk(periodeList)
    val beredskap = lagBeredskap(periodeList)
    val tilsynsordning = lagTilsynsordning(periodeList)
    val lovbestemtFerie = lagLovbestemtFerie(periodeList)
    val bosteder = lagBosteder(periodeList)
    val utenlandsopphold = lagUtenlandsopphold(periodeList)
    val arbeidstaker = lagArbeidstaker(periodeList)
    val arbeidstid = Arbeidstid().medArbeidstaker(listOf(arbeidstaker))
    
    return PleiepengerSyktBarn()
        .medSøknadsperiode(periodeList)
        .medSøknadInfo(søknadInfo)
        .medInfoFraPunsj(infoFraPunsj)
        .medBarn(barn)
        .medBeredskap(beredskap)
        .medNattevåk(nattevåk)
        .medTilsynsordning(tilsynsordning)
        .medArbeidstid(arbeidstid)
        .medUttak(uttak)
        .medUtenlandsopphold(utenlandsopphold)
        .medOmsorg(omsorg)
        .medLovbestemtFerie(lovbestemtFerie)
        .medBosteder(bosteder)
}

fun lagUtenlandsopphold(periodeList: List<Periode>): Utenlandsopphold {
    val utenlandsoppholdPeriodeInfo = UtenlandsoppholdPeriodeInfo()
        .medLand(Landkode.FINLAND)
        .medÅrsak(Utenlandsopphold.UtenlandsoppholdÅrsak.BARNET_INNLAGT_I_HELSEINSTITUSJON_FOR_NORSK_OFFENTLIG_REGNING)
    return Utenlandsopphold().medPerioder(
        lagPerioder(periodeList, utenlandsoppholdPeriodeInfo)
    )
}

fun lagBosteder(periodeList: List<Periode>): Bosteder {
    val bostedPeriodeInfo = BostedPeriodeInfo()
        .medLand(Landkode.NORGE)
    return Bosteder().medPerioder(
        lagPerioder(periodeList, bostedPeriodeInfo)
    )
}

fun lagLovbestemtFerie(periodeList: List<Periode>): LovbestemtFerie {
    val lovbestemtFeriePeriodeInfo = LovbestemtFeriePeriodeInfo()
    return LovbestemtFerie().medPerioder(
        lagPerioder(periodeList, lovbestemtFeriePeriodeInfo)
    )
}

fun lagTilsynsordning(periodeList: List<Periode>): Tilsynsordning {
    val tilsynPeriodeInfo = TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(Duration.ofHours(7).plusMinutes(30))
    return Tilsynsordning().medPerioder(
        lagPerioder(periodeList, tilsynPeriodeInfo)
    )
}

fun lagNattevåk(periodeList: List<Periode>): Nattevåk {
    val nattevåkPeriodeInfo = NattevåkPeriodeInfo().medTilleggsinformasjon("")
    return Nattevåk().medPerioder(
        lagPerioder(periodeList, nattevåkPeriodeInfo)
    )
}

fun lagBeredskap(periodeList: List<Periode>): Beredskap {
    val beredskapPeriodeInfo = BeredskapPeriodeInfo().medTilleggsinformasjon("")
    return Beredskap().medPerioder(
        lagPerioder(periodeList, beredskapPeriodeInfo)
    )
}

fun lagArbeidstaker(periodeList: List<Periode>): Arbeidstaker {
    val arbeidstidPeriodeInfo =
        ArbeidstidPeriodeInfo(Duration.ofHours(7).plusMinutes(30), Duration.ofHours(7).plusMinutes(30))
    return Arbeidstaker(
        null, Organisasjonsnummer.of("999999999"),
        ArbeidstidInfo(
            lagPerioder(periodeList, arbeidstidPeriodeInfo)
        )
    )
}

fun lagUttak(periodeList: List<Periode>): Uttak {
    val uttakPeriodeInfo = UttakPeriodeInfo(Duration.ofHours(7).plusMinutes(30))
    return Uttak().medPerioder(lagPerioder(periodeList, uttakPeriodeInfo))
}

fun <T> lagPerioder(periodeList: List<Periode>, periodeInfo: T): HashMap<Periode, T> {
    val resultatMap = HashMap<Periode, T>()
    for (periode in periodeList) {
        resultatMap[periode] = periodeInfo
    }
    return resultatMap
}
