package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_INGEN_PERIODER
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_INGEN_SAK
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_MER_ENN_EN_SAK
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE

sealed interface SakVurdering {
    /** @param oppslagsperiode overlappet mellom saken og endringsperioden. Arbeidsgiverne hentes for den. */
    data class Tilgang(val sak: SakForTilgang, val oppslagsperiode: Datoperiode) : SakVurdering
    data class IngenTilgang(val årsak: IngenTilgangÅrsak) : SakVurdering
}

/** Stopper ved første treff, så avslag gir alltid én årsak. */
fun vurderSaker(saker: List<SakForTilgang>, endringsperiode: Datoperiode): SakVurdering {
    // Eldre saker blokkerer ikke og telles ikke som flere saker.
    val aktuelle = saker.filterNot { it.erEldreEnn(endringsperiode) }

    if (saker.isEmpty())
        return SakVurdering.IngenTilgang(HAR_INGEN_SAK)
    if (aktuelle.isEmpty())
        return SakVurdering.IngenTilgang(SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE)
    if (aktuelle.size > 1)
        return SakVurdering.IngenTilgang(HAR_MER_ENN_EN_SAK)

    val sak = aktuelle.single()

    // Feil i dataene/barn er anonymisert
    check(sak.barnHarIdentitetsnummer) { "Barnet i saken mangler identitetsnummer." }

    val samletSøknadsperiode = sak.samletSøknadsperiode()
        ?: return SakVurdering.IngenTilgang(HAR_INGEN_PERIODER)

    // Saken er ikke eldre, så manglende overlapp betyr at den starter etter endringsperioden.
    val oppslagsperiode = samletSøknadsperiode.overlappendePeriode(endringsperiode)
        ?: return SakVurdering.IngenTilgang(SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE)

    return SakVurdering.Tilgang(sak = sak, oppslagsperiode = oppslagsperiode)
}

/** Alle søknadsperiodene slutter før endringsperioden */
private fun SakForTilgang.erEldreEnn(endringsperiode: Datoperiode): Boolean =
    søknadsperioder.isNotEmpty() && søknadsperioder.all { it.tom.isBefore(endringsperiode.fom) }

/** Tidligste fom til seneste tom. Null uten perioder. */
private fun SakForTilgang.samletSøknadsperiode(): Datoperiode? {
    if (søknadsperioder.isEmpty()) return null
    return Datoperiode(
        fom = søknadsperioder.minOf { it.fom },
        tom = søknadsperioder.maxOf { it.tom },
    )
}
