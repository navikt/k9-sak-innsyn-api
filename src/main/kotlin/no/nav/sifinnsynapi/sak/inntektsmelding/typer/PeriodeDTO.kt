package no.nav.sifinnsynapi.sak.inntektsmelding.typer

import java.time.LocalDate

data class PeriodeDTO(
    val fom: LocalDate,
    val tom: LocalDate
)
