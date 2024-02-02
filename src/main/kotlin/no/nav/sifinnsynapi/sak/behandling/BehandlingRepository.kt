package no.nav.sifinnsynapi.sak.behandling

import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Stream

@Transactional(TRANSACTION_MANAGER)
interface BehandlingRepository : JpaRepository<BehandlingDAO, String> {
    fun findAllByPleietrengendeAktørIdOrderByOppdatertDatoAsc(pleietrengendeAktørIder: String): Stream<BehandlingDAO>
}
