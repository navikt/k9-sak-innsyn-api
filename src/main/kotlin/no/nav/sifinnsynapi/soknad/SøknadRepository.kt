package no.nav.sifinnsynapi.soknad

import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import org.hibernate.annotations.QueryHints.READ_ONLY
import org.hibernate.jpa.QueryHints.HINT_CACHEABLE
import org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Stream
import javax.persistence.QueryHint

@Transactional(TRANSACTION_MANAGER)
interface SøknadRepository : JpaRepository<PsbSøknadDAO, String> {

    @QueryHints(
        value = [
            QueryHint(name = HINT_FETCH_SIZE, value = "10"),
            QueryHint(name = HINT_CACHEABLE, value = "false"),
            QueryHint(name = READ_ONLY, value = "true")
        ]
    )
    @Query(
        nativeQuery = true,
        value = "SELECT * FROM psb_søknad WHERE pleietrengende_aktør_id IN ?1 ORDER BY oppdatert_dato ASC"
    )
    fun hentSøknaderSortertPåOppdatertTidspunkt(
        pleietrengendeAktørIder: List<String>
    ): Stream<PsbSøknadDAO>
}
