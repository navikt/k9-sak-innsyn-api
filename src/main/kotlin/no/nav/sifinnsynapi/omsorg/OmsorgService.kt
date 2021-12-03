package no.nav.sifinnsynapi.omsorg

import org.springframework.stereotype.Service

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

    fun oppdaterOmsorg(søkerAktørId: String, pleietrengendeAktørId: String, harOmsorgen: Boolean): OmsorgDAO {
        val omsorgDAO =
            hentOmsorg(søkerAktørId, pleietrengendeAktørId) ?: throw OmsorgIkkeFunnetException("Omsorg ikke funnet.")
        return omsorgRepository.save(omsorgDAO.copy(harOmsorgen = harOmsorgen))
    }

    fun lagre(omsorgDAO: OmsorgDAO): OmsorgDAO {
        return omsorgRepository.save(omsorgDAO)
    }
}

class OmsorgIkkeFunnetException(override val message: String) : RuntimeException(message)
