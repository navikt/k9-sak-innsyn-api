package no.nav.sifinnsynapi.sak.behandling.converters

import jakarta.persistence.AttributeConverter
import no.nav.k9.kodeverk.behandling.FagsakYtelseType

internal class FagsakYtelseTypeConverter : AttributeConverter<FagsakYtelseType, String> {

    override fun convertToEntityAttribute(fagssakYtelsetypeKode: String): FagsakYtelseType =
        FagsakYtelseType.fraKode(fagssakYtelsetypeKode)

    override fun convertToDatabaseColumn(fagsakYtelseType: FagsakYtelseType): String = fagsakYtelseType.kode
}
