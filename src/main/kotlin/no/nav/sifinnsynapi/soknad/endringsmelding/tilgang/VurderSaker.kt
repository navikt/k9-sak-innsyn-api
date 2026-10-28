package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_INGEN_PERIODER
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_INGEN_SAK
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_MER_ENN_EN_SAK
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE

sealed interface SakVurdering {
    /**
     * @param oppslagsperiode overlappet mellom samlet søknadsperiode og endringsperioden. Arbeidsgiverne hentes
     * for denne perioden.
     */
    data class Tilgang(val sak: SakForTilgang, val oppslagsperiode: Datoperiode) : SakVurdering
    data class IngenTilgang(val årsak: IngenTilgangÅrsak) : SakVurdering
}

/**
 * Vurderer sakene, før arbeidsgiveroppslaget. Oversikt over alle reglene: [EndringsmeldingTilgangService].
 *
 * Reglene evalueres i rekkefølge og stopper ved første treff, så avslag gir alltid én årsak.
 */
fun vurderSaker(saker: List<SakForTilgang>, endringsperiode: Datoperiode): SakVurdering {
    // Eldre saker blokkerer ikke og telles ikke med når vi ser etter flere saker. De skilles ut for å gi
    // riktig årsak når bruker bare har gamle saker.
    val aktuelle = saker.filterNot { it.erEldreEnn(endringsperiode) }

    if (saker.isEmpty()) return SakVurdering.IngenTilgang(HAR_INGEN_SAK)
    if (aktuelle.isEmpty()) return SakVurdering.IngenTilgang(SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE)
    if (aktuelle.size > 1) return SakVurdering.IngenTilgang(HAR_MER_ENN_EN_SAK)

    val sak = aktuelle.single()

    // Saken kan ikke sendes inn uten barnets identitetsnummer. Det er en feil i dataene, ikke et avslag.
    // Meldingen havner i svaret og loggen, og skal derfor ikke inneholde personopplysninger.
    check(sak.barnHarIdentitetsnummer) { "Barnet i saken mangler identitetsnummer." }

    // En sak uten søknadsperioder er aldri eldre, se erEldreEnn. Derfor havner den her.
    val samletSøknadsperiode = sak.samletSøknadsperiode()
        ?: return SakVurdering.IngenTilgang(HAR_INGEN_PERIODER)

    // Saken er ikke eldre, så manglende overlapp betyr at den starter etter endringsperioden.
    val oppslagsperiode = samletSøknadsperiode.fellesPeriode(endringsperiode)
        ?: return SakVurdering.IngenTilgang(SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE)

    return SakVurdering.Tilgang(sak = sak, oppslagsperiode = oppslagsperiode)
}

/**
 * En sak er eldre når *alle* søknadsperiodene slutter før endringsperioden starter.
 * En sak uten søknadsperioder er ikke eldre. Den er aktuell og gir HAR_INGEN_PERIODER.
 */
private fun SakForTilgang.erEldreEnn(endringsperiode: Datoperiode): Boolean =
    søknadsperioder.isNotEmpty() && søknadsperioder.all { it.tom.isBefore(endringsperiode.fom) }

/**
 * Fra tidligste fom til seneste tom over alle søknadsperiodene i saken. Null når saken ikke har perioder.
 */
private fun SakForTilgang.samletSøknadsperiode(): Datoperiode? {
    if (søknadsperioder.isEmpty()) return null
    return Datoperiode(
        fom = søknadsperioder.minOf { it.fom },
        tom = søknadsperioder.maxOf { it.tom },
    )
}
