package no.nav.sifinnsynapi.sak.inntektsmelding.typer

import java.math.BigDecimal

data class NaturalYtelseDTO(
    val periode: PeriodeDTO?,
    val beløpPerMnd: BigDecimal,
    val type: NaturalYtelseTypeDTO
)
