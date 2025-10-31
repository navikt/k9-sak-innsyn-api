package no.nav.sifinnsynapi.enhetsregisteret

data class OrganisasjonRespons(val navn: OrganisasjonNavnRespons?) {
    fun hentNavn(): String? {
        return navn?.sammensattnavn
    }
}
