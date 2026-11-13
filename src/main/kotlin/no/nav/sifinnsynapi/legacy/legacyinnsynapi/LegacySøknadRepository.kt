package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(TRANSACTION_MANAGER)
interface LegacySøknadRepository : JpaRepository<LegacySøknadDAO, String> {

    @Query(
        nativeQuery = true,
        value = "SELECT * FROM legacy_søknad WHERE søknad_id = ?1 AND (søknad -> 'søker' ->> 'fødselsnummer') = ?2"
    )
    fun finnForSøknadIdOgFødselsnummer(søknadId: String, fødselsnummer: String): LegacySøknadDAO?

    @Query(
        nativeQuery = true,
        value = "SELECT * FROM legacy_søknad WHERE søknad_id IN (?1) AND (søknad -> 'søker' ->> 'fødselsnummer') = ?2"
    )
    fun finnAlleForSøknadIderOgFødselsnummer(søknadIder: Collection<String>, fødselsnummer: String): List<LegacySøknadDAO>
}

