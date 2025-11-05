package no.nav.sifinnsynapi.sak.inntektsmelding.typer

import java.math.BigDecimal
import java.time.LocalDate

data class EndringRefusjonDTO(
    val refusjonBeløpPerMnd: BigDecimal,
    val fom: LocalDate
)
