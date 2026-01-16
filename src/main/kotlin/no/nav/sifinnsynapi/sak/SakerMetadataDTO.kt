package no.nav.sifinnsynapi.sak

import no.nav.k9.innsyn.sak.FagsakYtelseType
import java.time.ZonedDateTime

data class SakerMetadataDTO(
    val saksnummer: String,
    val pleietrengende: PleietrengendeDTO,
    val fagsakYtelseType: FagsakYtelseType,
    val fagsakOpprettetTidspunkt: ZonedDateTime?,
    val fagsakAvsluttetTidspunkt: ZonedDateTime?
)
