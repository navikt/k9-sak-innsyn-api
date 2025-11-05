package no.nav.sifinnsynapi.sak.inntektsmelding.typer

data class UtsettelseDTO(
    val periode: PeriodeDTO,
    val årsak: UtsettelseÅrsakDTO
)
