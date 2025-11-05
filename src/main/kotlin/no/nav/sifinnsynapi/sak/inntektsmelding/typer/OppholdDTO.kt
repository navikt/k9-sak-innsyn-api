package no.nav.sifinnsynapi.sak.inntektsmelding.typer

import java.time.Duration

data class OppholdDTO(
    val periode: PeriodeDTO,
    val varighetPerDag: Duration?
)
