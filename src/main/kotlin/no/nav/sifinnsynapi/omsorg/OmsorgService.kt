package no.nav.sifinnsynapi.omsorg

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OmsorgService(
    private val omsorgRepository: OmsorgRepository
) {

    fun harOmsorgen(søkerAktørId: String, pleietrengendeAktørId: String): Boolean =
        hentOmsorg(søkerAktørId, pleietrengendeAktørId)?.harOmsorgen ?: false

    fun omsorgEksisterer(søkerAktørId: String, pleietrengendeAktørId: String): Boolean =
        omsorgRepository.existsBySøkerAktørIdAndPleietrengendeAktørId(søkerAktørId, pleietrengendeAktørId)

    fun hentOmsorg(søkerAktørId: String, pleietrengendeAktørId: String): OmsorgDAO? =
        omsorgRepository.findBySøkerAktørIdAndPleietrengendeAktørId(søkerAktørId, pleietrengendeAktørId)

    /**
     * Oppdaterer omsorgen for pleietrengende.
     * @param harOmsorgen: boolsk verdi for omsorgen. 'True' hvis søker har omsorg for pleietrengende, 'false' ellers.
     * @param søkerAktørId: AktørId på søker registrert i DB.
     * @param pleietrengendeAktørId: AktørId på pleietrengende registrert i DB.
     * @return true hvis raden ble oppdatert.
     * @throws OmsorgIkkeFunnetException hvis oppdatering feilet. Antakligvis fordi kombinasjon av søkerAktørId og pleietrengendeAktørId ikke ble funnet.
     */
    fun oppdaterOmsorg(søkerAktørId: String, pleietrengendeAktørId: String, harOmsorgen: Boolean): Boolean {
        val oppdatertOmsorg = omsorgRepository.oppdaterOmsorg(
            harOmsorgen = harOmsorgen,
            søkerAktørId = søkerAktørId,
            pleietrengendeAktørId = pleietrengendeAktørId
        )
        if (oppdatertOmsorg == 1) return true
        else throw OmsorgIkkeFunnetException("Omsorg ikke funnet/oppdatert.")
    }

    fun lagreOmsorg(omsorgDAO: OmsorgDAO): OmsorgDAO {
        return omsorgRepository.save(omsorgDAO)
    }
}

class OmsorgIkkeFunnetException(override val message: String) : RuntimeException(message)
