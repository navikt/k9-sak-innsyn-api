package no.nav.sifinnsynapi.sak.inntektsmelding.typer

import com.fasterxml.jackson.annotation.JsonProperty

data class UtsettelseDTO(
    val periode: PeriodeDTO,
    @get:JsonProperty("årsak") val årsak: UtsettelseÅrsakDTO
)
