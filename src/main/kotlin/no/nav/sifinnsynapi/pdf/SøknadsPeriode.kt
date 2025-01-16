package no.nav.sifinnsynapi.pdf

import java.time.LocalDate

data class SøknadsPeriode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate
)