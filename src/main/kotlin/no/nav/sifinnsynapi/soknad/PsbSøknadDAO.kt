package no.nav.sifinnsynapi.soknad

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import org.springframework.data.annotation.CreatedDate
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@TypeDefs(
    TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
@Entity(name = "psb_søknad")
data class PsbSøknadDAO(
    @Column(name = "journalpost_id") @Id val journalpostId: String,
    @Column(name = "søker_aktør_id") val søkerAktørId: String,
    @Column(name = "pleietrengende_aktør_id") val pleietrengendeAktørId: String,
    @Column(name = "søknad", columnDefinition = "jsonb") @Type(type = "jsonb") val søknad: String,
    @Column(name = "opprettet_dato") @CreatedDate val opprettetDato: ZonedDateTime = ZonedDateTime.now(UTC),
    @Column(name = "oppdatert_dato") val oppdatertDato: ZonedDateTime = ZonedDateTime.now(UTC)
) {
    override fun toString(): String {
        return "SøknadDAO(journalpostId=$journalpostId, opprettetDato=$opprettetDato, oppdatertDato=$oppdatertDato)"
    }
}
