package no.nav.sifinnsynapi.sak.behandling

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.UUID

@Entity(name = "behandling")
data class BehandlingDAO(
    @Column(name = "behandling_id") @Id val behandlingId: UUID,
    @Column(name = "søker_aktør_id") val søkerAktørId: String,
    @Column(name = "pleietrengende_aktør_id") val pleietrengendeAktørId: String,
    @Column(name = "saksnummer") val saksnummer: String,
    @Column(name = "ytelsetype_kode") val ytelsetypeKode: String,
    @Column(name = "behandling", columnDefinition = "jsonb") @JdbcTypeCode(SqlTypes.JSON) val behandling: String,
    @Column(name = "opprettet_dato") @CreatedDate val opprettetDato: ZonedDateTime = ZonedDateTime.now(UTC),
    @Column(name = "oppdatert_dato") val oppdatertDato: ZonedDateTime = ZonedDateTime.now(UTC),
) {
    override fun toString(): String {
        return "BehandlingDAO(behandlingId=$behandlingId, opprettetDato=$opprettetDato, oppdatertDato=$oppdatertDato)"
    }
}
