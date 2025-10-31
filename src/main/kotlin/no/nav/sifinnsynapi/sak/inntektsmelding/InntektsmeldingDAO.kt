package no.nav.sifinnsynapi.sak.inntektsmelding

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import no.nav.k9.innsyn.inntektsmelding.InntektsmeldingStatus
import no.nav.k9.innsyn.sak.FagsakYtelseType
import no.nav.sifinnsynapi.sak.behandling.converters.FagsakYtelseTypeConverter
import no.nav.sifinnsynapi.sak.behandling.converters.InntektsmeldingStatusConverter
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

@Entity(name = "inntektsmelding")
data class InntektsmeldingDAO(
    @Column(name = "journalpost_id") @Id val journalpostId: String,
    @Column(name = "søker_aktør_id") val søkerAktørId: String,
    @Column(name = "saksnummer") val saksnummer: String,
    @Column(name = "inntektsmelding", columnDefinition = "jsonb") @JdbcTypeCode(SqlTypes.JSON) val inntektsmelding: String,

    @Convert(converter = FagsakYtelseTypeConverter::class)
    @Column(name = "ytelsetype") val ytelsetype: FagsakYtelseType,

    @Convert(converter = InntektsmeldingStatusConverter::class)
    @Column(name = "status") val status: InntektsmeldingStatus,

    @Column(name = "opprettet_dato") @CreatedDate val opprettetDato: ZonedDateTime = ZonedDateTime.now(UTC),
    @Column(name = "oppdatert_dato") val oppdatertDato: ZonedDateTime = ZonedDateTime.now(UTC),
) {
    override fun toString(): String {
        return "InntektsmeldingDAO(journalpostId='$journalpostId', saksnummer='$saksnummer', ytelsetype=$ytelsetype, opprettetDato=$opprettetDato, oppdatertDato=$oppdatertDato)"
    }
}
