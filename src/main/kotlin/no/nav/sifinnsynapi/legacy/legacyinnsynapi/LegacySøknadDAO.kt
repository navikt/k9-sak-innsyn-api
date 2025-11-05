package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

@Entity(name = "legacy_søknad")
data class LegacySøknadDAO(
    @Column(name = "søknad_id") @Id val søknadId: String,
    @Column(name = "søknadstype") val søknadstype: String,
    @Column(name = "søknad", columnDefinition = "jsonb") @JdbcTypeCode(SqlTypes.JSON) val søknad: String,
    @Column(name = "saks_id") val saksId: String?,
    @Column(name = "journalpost_id") val journalpostId: String,
    @Column(name = "opprettet_dato") @CreatedDate val opprettetDato: ZonedDateTime = ZonedDateTime.now(UTC),
) {
    override fun toString(): String {
        return "LegacySøknadDAO(søknadId=$søknadId, søknadstype=$søknadstype, opprettetDato=$opprettetDato)"
    }
}

