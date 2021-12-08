package no.nav.sifinnsynapi.soknad

import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Stream

@Transactional(TRANSACTION_MANAGER)
interface SøknadRepository : JpaRepository<PsbSøknadDAO, String> {

    @Query(
        nativeQuery = true,
        value = "SELECT * FROM psb_søknad WHERE pleietrengende_aktør_id = ?1 ORDER BY oppdatert_dato ASC"
    )
    fun hentSøknaderPåPleietrengendeSortertPåOppdatertTidspunkt(pleietrengendeAktørIder: String): Stream<PsbSøknadDAO>
}
