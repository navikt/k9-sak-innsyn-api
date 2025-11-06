package no.nav.sifinnsynapi.sak.behandling

import no.nav.k9.innsyn.sak.FagsakYtelseType

data class PleietrengendeAktørIdMedSaksnummer(
    val saksnummer: String,
    val pleietrengendeAktørId: String,
    val ytelsetype: FagsakYtelseType
)
