package no.nav.sifinnsynapi.sak.behandling

import no.nav.k9.innsyn.sak.FagsakYtelseType
import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.stream.Stream

@Transactional(TRANSACTION_MANAGER)
interface BehandlingRepository : JpaRepository<BehandlingDAO, UUID> {
    fun findAllBySøkerAktørIdAndPleietrengendeAktørIdAndYtelsetypeOrderByOppdatertDatoAsc(søkerAktørId: String, pleietrengendeAktørIder: String, ytelsetype: FagsakYtelseType): Stream<BehandlingDAO>
    fun findAllBySøkerAktørIdAndYtelsetypeOrderByOppdatertDatoAsc(søkerAktørId: String, ytelsetype: FagsakYtelseType): Stream<BehandlingDAO>

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
        value = "UPDATE behandling SET søker_aktør_id = ?1 WHERE søker_aktør_id = ?2"
    )
    fun oppdaterAktørIdForSøker(gyldigAktørId: String, utgåttAktørId: String): Int

    @Query(
        nativeQuery = true,
        value = "SELECT DISTINCT saksnummer, pleietrengende_aktør_id, ytelsetype FROM behandling " +
                "WHERE søker_aktør_id = ?1 AND pleietrengende_aktør_id IN (?2) AND ytelsetype = ?3"
    )
    fun hentSaksnummere(søkerAktørId: String, pleietrengendeAktørIder: Set<String>, ytelsetype: String): List<PleietrengendeAktørIdMedSaksnummer>
}
