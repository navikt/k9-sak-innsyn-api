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

    @Transactional
    fun oppdaterOmsorg(søkerAktørId: String, pleietrengendeAktørId: String, harOmsorgen: Boolean): Boolean {
        val oppdatertOmsorg = omsorgRepository.oppdaterOmsorg(
            harOmsorgen = harOmsorgen,
            søkerAktørId = søkerAktørId,
            pleietrengendeAktørId = pleietrengendeAktørId
        )
        if (oppdatertOmsorg == 1) return true
        else throw OmsorgIkkeFunnetException("Omsorg ikke funnet/oppdatert.")
    }

    fun lagre(omsorgDAO: OmsorgDAO): OmsorgDAO {
        return omsorgRepository.save(omsorgDAO)
    }
}

class OmsorgIkkeFunnetException(override val message: String) : RuntimeException(message)
