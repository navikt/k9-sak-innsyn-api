package no.nav.sifinnsynapi.sak.inntektsmelding

data class ArbeidsgiverDTO(
    val organisasjon: ArbeidsgiverOrganisasjonDTO?,
    val privat: ArbeidsgiverPrivatDTO?
) {
    init {
        require((organisasjon != null && privat == null) || (organisasjon == null && privat != null)) {
            "Arbeidsgiver kan kun ha en av de to feltene satt, og ikke begge."
        }
    }
}

data class ArbeidsgiverPrivatDTO(
    val navn: String?,
    val fødselsnummer: String
)

data class ArbeidsgiverOrganisasjonDTO(
    val navn: String?,
    val organisasjonsnummer: String,
)
