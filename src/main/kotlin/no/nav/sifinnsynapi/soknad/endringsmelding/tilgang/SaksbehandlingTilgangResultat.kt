package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

/**
 * Respons fra POST /saksbehandling/psb/endringsmelding/tilgang.
 *
 * [tekst] vises direkte for saksbehandleren i k9-sak-web. Ved avslag er det teksten for hovedårsaken.
 */
data class SaksbehandlingTilgangResultat(
    val kanBruke: Boolean,
    val tekst: String,
) {
    companion object {
        fun fra(resultat: TilgangResultat) =
            if (resultat.kanBruke) {
                SaksbehandlingTilgangResultat(kanBruke = true, tekst = "Ja, har tilgang")
            } else {
                // Første årsak er hovedårsaken.
                SaksbehandlingTilgangResultat(kanBruke = false, tekst = resultat.årsaker.first().saksbehandlerTekst)
            }
    }
}
