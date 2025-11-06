package no.nav.sifinnsynapi.sak

import no.nav.k9.innsyn.sak.FagsakYtelseType

data class SakerMetadataDTO(
    val saksnummer: String,
    val pleietrengende: PleietrengendeDTO,
    val fagsakYtelseType: FagsakYtelseType,
)
