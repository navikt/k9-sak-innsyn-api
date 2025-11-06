package no.nav.sifinnsynapi.sak.behandling

data class PleietrengendeAktørIdMedSaksnummer(
    val saksnummer: String,
    val pleietrengendeAktørId: String,
    val ytelsetype: String,
)
