package no.nav.sifinnsynapi.soknad

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.common.PersonIdentifikator
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.annotation.CreatedDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*
import javax.persistence.*

@TypeDefs(
    TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
@Entity(name = "søknad")
data class SøknadDAO(
    @Column(name = "id") @Id @Type(type = "pg-uuid") val id: UUID = UUID.randomUUID(),
    @Column(name = "søknad_id") @Type(type = "pg-uuid") val søknadId: UUID,
    @Column(name = "person_ident") @Embedded val personIdent: PersonIdentifikator,
    @Column(name = "søknad", columnDefinition = "jsonb") @Type(type = "jsonb") val søknad: Søknad,
    @Column(name = "opprettet") @CreatedDate val opprettet: ZonedDateTime? = null,
    @Column(name = "endret") @UpdateTimestamp val endret: LocalDateTime? = null,
    @Column(name = "behandlingsdato") val behandlingsdato: LocalDate? = null
) {
    override fun toString(): String {
        return "SøknadDAO(id=$id, søknadId=$søknadId, opprettet=$opprettet, endret=$endret, behandlingsdato=$behandlingsdato)"
    }
}
