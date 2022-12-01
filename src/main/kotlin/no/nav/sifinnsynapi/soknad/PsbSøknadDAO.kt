package no.nav.sifinnsynapi.soknad

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

@Entity(name = "psb_søknad")
data class PsbSøknadDAO(
    @Column(name = "journalpost_id") @Id val journalpostId: String,
    @Column(name = "søker_aktør_id") val søkerAktørId: String,
    @Column(name = "pleietrengende_aktør_id") val pleietrengendeAktørId: String,
    @Column(name = "søknad", columnDefinition = "jsonb") @JdbcTypeCode(SqlTypes.JSON) val søknad: String,
    @Column(name = "opprettet_dato") @CreatedDate val opprettetDato: ZonedDateTime = ZonedDateTime.now(UTC),
    @Column(name = "oppdatert_dato") val oppdatertDato: ZonedDateTime = ZonedDateTime.now(UTC),
) {
    override fun toString(): String {
        return "SøknadDAO(journalpostId=$journalpostId, opprettetDato=$opprettetDato, oppdatertDato=$oppdatertDato)"
    }
}
