package no.nav.sifinnsynapi.soknad

import no.nav.sifinnsynapi.common.PersonIdentifikator
import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional(TRANSACTION_MANAGER)
interface SøknadRepository : JpaRepository<SøknadDAO, UUID> {
    fun existsBySøknadId(søknadId: UUID): Boolean
    fun findAllByPersonIdent(personIdent: PersonIdentifikator): List<SøknadDAO>
    fun findBySøknadId(søknadId: UUID): SøknadDAO?
}
