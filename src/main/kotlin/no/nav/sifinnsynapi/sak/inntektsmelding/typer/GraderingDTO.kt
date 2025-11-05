package no.nav.sifinnsynapi.sak.inntektsmelding.typer

import java.math.BigDecimal

data class GraderingDTO(
    val periode: PeriodeDTO,
    val arbeidstidProsent: BigDecimal
)
