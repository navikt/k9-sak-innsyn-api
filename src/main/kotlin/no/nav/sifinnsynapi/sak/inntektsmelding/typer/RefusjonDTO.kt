package no.nav.sifinnsynapi.sak.inntektsmelding.typer

import no.nav.k9.søknad.TidUtils.TIDENES_ENDE
import java.math.BigDecimal
import java.time.LocalDate

data class RefusjonDTO(
    val refusjonBeløpPerMnd: BigDecimal,
    val refusjonOpphører: LocalDate?,
) {
    val utledetRefusjon: UtledetRefusjonType
        get() {
            return when (refusjonOpphører) {
                null, TIDENES_ENDE -> UtledetRefusjonType.OPPHØRER_ALDRI
                else -> UtledetRefusjonType.OPPHØRER
            }
        }
}

enum class UtledetRefusjonType {
    OPPHØRER,
    OPPHØRER_ALDRI
}
