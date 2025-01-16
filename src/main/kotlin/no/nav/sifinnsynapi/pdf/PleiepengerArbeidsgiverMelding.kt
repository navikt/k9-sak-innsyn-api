package no.nav.sifinnsynapi.pdf

data class PleiepengerArbeidsgiverMelding(
    val arbeidstakernavn: String,
    val arbeidsgivernavn: String? = null,
    val søknadsperiode: SøknadsPeriode
)