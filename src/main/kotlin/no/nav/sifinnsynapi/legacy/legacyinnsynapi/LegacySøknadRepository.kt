package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(TRANSACTION_MANAGER)
interface LegacySøknadRepository : JpaRepository<LegacySøknadDAO, String>

