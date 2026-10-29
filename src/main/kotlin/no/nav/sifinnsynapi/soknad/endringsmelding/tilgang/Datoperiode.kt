package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import java.time.LocalDate
import java.time.temporal.WeekFields

/** Lukket periode der både [fom] og [tom] er med. Pågående ansettelser gjøres om med [Ansettelsesperiode.lukk]. */
data class Datoperiode(val fom: LocalDate, val tom: LocalDate) {
    init {
        // Meldingen kan havne i feilresponsen, så den inneholder ikke datoene.
        require(!tom.isBefore(fom)) { "Ugyldig datoperiode: tom er før fom." }
    }

    /** Én felles dag regnes som overlapp. */
    fun overlapper(annen: Datoperiode): Boolean = !fom.isAfter(annen.tom) && !annen.fom.isAfter(tom)

    /** Dagene som er med i begge periodene, eller null hvis de ikke overlapper. */
    fun fellesPeriode(annen: Datoperiode): Datoperiode? {
        val fom = maxOf(fom, annen.fom)
        val tom = minOf(tom, annen.tom)
        return if (tom.isBefore(fom)) null else Datoperiode(fom, tom)
    }
}

/** Ansettelsesperiode fra AA-registeret. [tom] mangler når ansettelsen pågår. */
data class Ansettelsesperiode(val fom: LocalDate, val tom: LocalDate?) {
    /** Bruker tom fra [utfylling] når [tom] mangler. Null hvis tom da er før fom, som når en pågående ansettelse starter etter [utfylling]. */
    fun lukk(utfylling: Datoperiode): Datoperiode? {
        val tom = tom ?: utfylling.tom
        return if (tom.isBefore(fom)) null else Datoperiode(fom, tom)
    }
}

/** Isouke: mandag–søndag */
fun LocalDate.erSammeIsoukeSom(annen: LocalDate): Boolean =
    get(WeekFields.ISO.weekBasedYear()) == annen.get(WeekFields.ISO.weekBasedYear()) &&
        get(WeekFields.ISO.weekOfWeekBasedYear()) == annen.get(WeekFields.ISO.weekOfWeekBasedYear())
