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
        value = "SELECT * FROM psb_søknad WHERE søker_aktør_id = ?1 AND pleietrengende_aktør_id = ?2 ORDER BY oppdatert_dato ASC"
    )
    fun hentSøknaderSortertPåOppdatertTidspunkt(
        søkerAktørId: String,
        pleietrengendeAktørIder: String
    ): Stream<PsbSøknadDAO>
}
