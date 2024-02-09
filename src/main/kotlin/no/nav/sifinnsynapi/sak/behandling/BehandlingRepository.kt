package no.nav.sifinnsynapi.sak.behandling

import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.stream.Stream

@Transactional(TRANSACTION_MANAGER)
interface BehandlingRepository : JpaRepository<BehandlingDAO, UUID> {
    fun findAllByPleietrengendeAktørIdAndYtelsetypeOrderByOppdatertDatoAsc(pleietrengendeAktørIder: String, ytelsetype: FagsakYtelseType): Stream<BehandlingDAO>
}
