package no.nav.sifinnsynapi.soknad

import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Stream

@Transactional(TRANSACTION_MANAGER)
interface SøknadRepository : JpaRepository<PsbSøknadDAO, String> {
    fun findAllByPleietrengendeAktørIdOrderByOppdatertDatoAsc(pleietrengendeAktørIder: String): Stream<PsbSøknadDAO>

    /**
     * Oppdaterer Aktørid for søker (aktørsplitt/merge)
     * @param gyldigAktørId: Gyldig aktørid
     * @param utgåttAktørId: Utgått aktørId
     * @return antall rader for utgått aktørid
     */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = "UPDATE psb_søknad SET søker_aktør_id = ?1 WHERE søker_aktør_id = ?2"
    )
    fun oppdaterAktørIdForSøker(gyldigAktørId: String, utgåttAktørId: String): Int

}
