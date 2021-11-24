package no.nav.sifinnsynapi.omsorg

import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.annotation.CreatedDate
import java.time.ZonedDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@Entity(name = "omsorg")
data class OmsorgDAO(
    @Column(name = "id") @Id val id: String,
    @Column(name = "søker_aktør_id") val søkerAktørId: String,
    @Column(name = "pleietrengende_aktør_id") val pleietrengendeAktørId: String,
    @Column(name = "har_omsorgen") val harOmsorgen: Boolean,
    @Column(name = "opprettet_dato") @CreatedDate val opprettetDato: ZonedDateTime? = null,
    @Column(name = "oppdatert_dato") @UpdateTimestamp val oppdatertDato: ZonedDateTime? = null
)
