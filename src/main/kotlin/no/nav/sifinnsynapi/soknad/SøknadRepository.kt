package no.nav.sifinnsynapi.soknad

import no.nav.sifinnsynapi.common.AktørId
import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional(TRANSACTION_MANAGER)
interface SøknadRepository : JpaRepository<SøknadDAO, UUID> {
    fun existsBySøknadId(søknadId: UUID): Boolean
    fun findAllByAktørId(aktørId: AktørId): List<SøknadDAO>
    fun findBySøknadId(søknadId: UUID): SøknadDAO?
}
