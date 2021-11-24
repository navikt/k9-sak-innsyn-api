package no.nav.sifinnsynapi.omsorg

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query


interface OmsorgRepository: JpaRepository<OmsorgDAO, String> {
    @Query(
        nativeQuery = true,
        value = "SELECT har_omsorgen FROM omsorg where søker_aktør_id = ?1 AND pleietrengende_aktør_id = ?2"
    )
    fun harOmsorgen(søkerAktørId: String, pleietrengendeAktørId: String): Boolean
}
