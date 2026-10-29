package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER
import java.time.temporal.ChronoUnit

/**
 * Kjører alle reglene og samler årsakene i fast rekkefølge, med hovedårsaken først.
 * Tom liste betyr tilgang.
 */
fun vurderArbeidsforhold(
    sak: SakForTilgang,
    arbeidsgivere: List<ArbeidsgiverForTilgang>,
    endringsperiode: Datoperiode,
): List<IngenTilgangÅrsak> {
    // Kjent = med i saken, se SakForTilgang.arbeidsgiverOrgnr.
    val (kjente, ukjente) = arbeidsgivere.partition { it.organisasjonsnummer in sak.arbeidsgiverOrgnr }

    return buildList {
        if (sak.harArbeidstidSomSelvstendigNæringsdrivende) {
            add(HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE)
        }

        if (kjente.any { it.harToAnsettelserSammeUkeMedOpphold(endringsperiode) }) {
            add(EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD)
        }

        val søknadsperioderIEndringsperioden = sak.søknadsperioder.mapNotNull { it.overlappendePeriode(endringsperiode) }
        if (ukjente.any { it.harFlereAnsettelserIPerioder(søknadsperioderIEndringsperioden) }) {
            add(HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER)
        }
    }
}

/** Periodene klippes til endringsperioden, så et opphold før den gir ikke avslag. */
private fun ArbeidsgiverForTilgang.harToAnsettelserSammeUkeMedOpphold(endringsperiode: Datoperiode): Boolean {
    val perioderIEndringsperioden = ansettelsesperioder
        .mapNotNull { it.lukk(endringsperiode) }
        .mapNotNull { it.overlappendePeriode(endringsperiode) }

    return perioderSlutterOgStarterSammeUkeMedOpphold(perioderIEndringsperioden)
}

/**
 * True når to perioder har et opphold som starter og slutter i samme isouke.
 * Eksempel: ansatt man–tir, ikke ansatt ons, ansatt tor–fre.
 */
fun perioderSlutterOgStarterSammeUkeMedOpphold(perioder: List<Datoperiode>): Boolean {
    val sortert = perioder.sortedBy { it.fom }
    if (sortert.size < 2) return false

    var dekketTilOgMed = sortert.first().tom
    for (periode in sortert.drop(1)) {
        // Tom 02.01 og fom 04.01 gir 2 dager, altså opphold 03.01.
        val harOpphold = ChronoUnit.DAYS.between(dekketTilOgMed, periode.fom) > 1
        if (harOpphold && periode.fom.erSammeIsoukeSom(dekketTilOgMed)) return true

        dekketTilOgMed = maxOf(dekketTilOgMed, periode.tom)
    }
    return false
}

/** Mer enn én ansettelsesperiode overlapper søknadsperiodene. Pågående ansettelser lukkes med søknadsperioden. */
private fun ArbeidsgiverForTilgang.harFlereAnsettelserIPerioder(søknadsperioder: List<Datoperiode>): Boolean {
    val antallSomOverlapper = ansettelsesperioder.count { ansettelse ->
        søknadsperioder.any {
            søknadsperiode -> ansettelse.lukk(søknadsperiode)?.overlapper(søknadsperiode) == true
        }
    }
    return antallSomOverlapper > 1
}
