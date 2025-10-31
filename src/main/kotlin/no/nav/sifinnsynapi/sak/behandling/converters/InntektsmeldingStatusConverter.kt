package no.nav.sifinnsynapi.sak.behandling.converters

import jakarta.persistence.AttributeConverter
import no.nav.k9.innsyn.inntektsmelding.InntektsmeldingStatus

internal class InntektsmeldingStatusConverter : AttributeConverter<InntektsmeldingStatus, String> {

    override fun convertToEntityAttribute(statusKode: String): InntektsmeldingStatus =
        InntektsmeldingStatus.fraKode(statusKode)

    override fun convertToDatabaseColumn(status: InntektsmeldingStatus): String = status.kode
}
