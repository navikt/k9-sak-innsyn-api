package no.nav.sifinnsynapi.soknad

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import no.nav.k9.søknad.Søknad
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.annotation.CreatedDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import javax.persistence.*

@TypeDefs(
    TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
@Entity(name = "psb_søknad")
data class PsbSøknadDAO(
    @Column(name = "journalpost_id") @Id val journalpostId: String,
    @Column(name = "søker_aktør_id") val søkerAktørId: String,
    @Column(name = "pleietrengende_aktør_id") val pleietrengendeAktørId: String,
    @Column(name = "søknad", columnDefinition = "jsonb") @Type(type = "jsonb") val søknad: String,
    @Column(name = "opprettet_dato") @CreatedDate val opprettetDato: ZonedDateTime? = null,
    @Column(name = "oppdatert_dato") @UpdateTimestamp val oppdatertDato: ZonedDateTime? = null
) {
    override fun toString(): String {
        return "SøknadDAO(journalpostId=$journalpostId, opprettetDato=$opprettetDato, oppdatertDato=$oppdatertDato)"
    }
}
