package no.nav.sifinnsynapi.sak.inntektsmelding.typer

import no.nav.k9.søknad.TidUtils.TIDENES_ENDE
import java.math.BigDecimal
import java.time.LocalDate

data class RefusjonDTO(
    val refusjonBeløpPerMnd: BigDecimal,
    var refusjonOpphører: LocalDate?,
) {
    init {
        // Setter refusjonOpphører til null dersom den er satt til TIDENES_ENDE.
        // Dette tolkes som at refusjon ikke opphører så lenge søker mottar ytelsen.
        if (refusjonOpphører == TIDENES_ENDE) {
            refusjonOpphører = null
        }
    }
}
