package no.nav.sifinnsynapi.omsorg

import org.springframework.data.jpa.repository.JpaRepository


interface OmsorgRepository : JpaRepository<OmsorgDAO, String> {
    fun existsBySøkerAktørIdAndPleietrengendeAktørId(søkerAktørId: String, pleietrengendeAktørId: String): Boolean
    fun findBySøkerAktørIdAndPleietrengendeAktørId(søkerAktørId: String, pleietrengendeAktørId: String): OmsorgDAO?
}
