package no.nav.sifinnsynapi.omsorg

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional


interface OmsorgRepository : JpaRepository<OmsorgDAO, String> {
    fun existsBySøkerAktørIdAndPleietrengendeAktørId(søkerAktørId: String, pleietrengendeAktørId: String): Boolean
    fun findBySøkerAktørIdAndPleietrengendeAktørId(søkerAktørId: String, pleietrengendeAktørId: String): OmsorgDAO?

    /**
     * Oppdaterer omsorgen for pleietrengende.
     * @param harOmsorgen: boolsk verdi for omsorgen. 'True' hvis søker har omsorg for pleietrengende, 'false' ellers.
     * @param søkerAktørId: AktørId på søker registrert i DB.
     * @param pleietrengendeAktørId: AktørId på pleietrengende registrert i DB.
     * @return '1' hvis raden ble oppdatert, '0' hvis det feilet. Antakligvis fordi kombinasjon av søkerAktørId og pleietrengendeAktørId ikke ble funnet.
     */
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = "UPDATE omsorg SET har_omsorgen = ?1 WHERE søker_aktør_id = ?2 AND pleietrengende_aktør_id = ?3"
    )
    fun oppdaterOmsorg(harOmsorgen: Boolean, søkerAktørId: String, pleietrengendeAktørId: String): Int

    /**
     * Henter alle pleietrengende som søker har omsorgen for.
     *
     * @param søkerAktørId AktørId på søker.
     * @return En liste med pleietrengendeAktørId.
     */
    @Query(
        nativeQuery = true,
        value = "SELECT pleietrengende_aktør_id FROM omsorg WHERE søker_aktør_id = ?1 AND har_omsorgen = true"
    )
    fun hentPleietrengendeSøkerHarOmsorgFor(søkerAktørId: String): List<String>

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
        value = "UPDATE omsorg SET søker_aktør_id = ?1 WHERE søker_aktør_id = ?2"
    )
    fun oppdaterAktørIdForSøker(gyldigAktørId: String, utgåttAktørId: String): Int

}
