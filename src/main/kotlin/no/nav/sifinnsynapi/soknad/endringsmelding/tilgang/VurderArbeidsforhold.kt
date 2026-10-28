package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER
import java.time.temporal.ChronoUnit

/**
 * Vurderer arbeidsforholdene, etter arbeidsgiveroppslaget. Oversikt over alle reglene:
 * [EndringsmeldingTilgangService].
 *
 * Alle reglene evalueres, og årsakene samles i fast rekkefølge. Første årsak er hovedårsaken.
 *
 * Returnerer tom liste når bruker har tilgang.
 */
fun vurderArbeidsforhold(
    sak: SakForTilgang,
    arbeidsgivere: List<ArbeidsgiverForTilgang>,
    endringsperiode: Datoperiode,
): List<IngenTilgangÅrsak> {
    // En arbeidsgiver er kjent når den er arbeidstaker i saken med minst én periode over 0 normaltimer.
    // Andre regnes som ukjente, som i søknadsflyten. Se SakForTilgang.arbeidsgiverOrgnr.
    val (kjente, ukjente) = arbeidsgivere.partition { it.organisasjonsnummer in sak.arbeidsgiverOrgnr }

    return buildList {
        if (sak.harArbeidstidSomSelvstendigNæringsdrivende) {
            add(HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE)
        }

        if (kjente.any { it.harToAnsettelserSammeUkeMedOpphold(endringsperiode) }) {
            add(EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD)
        }

        val søknadsperioderIEndringsperioden = sak.søknadsperioder.mapNotNull { it.fellesPeriode(endringsperiode) }
        if (ukjente.any { it.harFlereAnsettelserIPerioder(søknadsperioderIEndringsperioden) }) {
            add(HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER)
        }
    }
}

/**
 * Bare perioder innenfor endringsperioden teller. Et opphold før endringsperioden skal ikke gi avslag.
 */
private fun ArbeidsgiverForTilgang.harToAnsettelserSammeUkeMedOpphold(endringsperiode: Datoperiode): Boolean {
    val perioderIEndringsperioden = ansettelsesperioder
        .mapNotNull { it.fyllÅpneEnder(endringsperiode) }
        // Behold bare perioder som overlapper endringsperioden, og klipp dem til den.
        .mapNotNull { it.fellesPeriode(endringsperiode) }

    return perioderSlutterOgStarterSammeUkeMedOpphold(perioderIEndringsperioden)
}

/**
 * Gir true når det finnes et opphold på minst én dag mellom to perioder, og oppholdet starter og slutter
 * i samme isouke. Eksempel: ansatt man–tir, ikke ansatt ons, ansatt tor–fre.
 *
 * Perioder kan overlappe, og et opphold kan være dekket av en tidligere, lengre periode. Derfor
 * sammenlignes hver periode med `dekketTilOgMed` (seneste tom så langt), ikke med forrige periode.
 */
fun perioderSlutterOgStarterSammeUkeMedOpphold(perioder: List<Datoperiode>): Boolean {
    val sortert = perioder.sortedBy { it.fom }
    if (sortert.size < 2) return false

    var dekketTilOgMed = sortert.first().tom
    for (periode in sortert.drop(1)) {
        // Mer enn én dag mellom dekket tom og neste fom betyr minst én dag uten ansettelse.
        // Eksempel: tom 02.01 og fom 04.01 gir 2 dager, altså opphold 03.01.
        val harOpphold = ChronoUnit.DAYS.between(dekketTilOgMed, periode.fom) > 1
        if (harOpphold && periode.fom.erSammeIsoukeSom(dekketTilOgMed)) return true

        dekketTilOgMed = maxOf(dekketTilOgMed, periode.tom)
    }
    return false
}

/**
 * Mer enn én ansettelsesperiode overlapper søknadsperiodene.
 *
 * [søknadsperioder] er allerede klippet til endringsperioden. Åpne ender i en ansettelsesperiode fylles
 * med søknadsperioden den sammenlignes mot, så en åpen ansettelse overlapper alltid hvis den andre
 * enden tillater det.
 */
private fun ArbeidsgiverForTilgang.harFlereAnsettelserIPerioder(søknadsperioder: List<Datoperiode>): Boolean {
    val antallSomOverlapper = ansettelsesperioder.count { ansettelse ->
        søknadsperioder.any { søknadsperiode ->
            ansettelse.fyllÅpneEnder(søknadsperiode)?.overlapper(søknadsperiode) ?: false
        }
    }
    return antallSomOverlapper > 1
}
