package no.nav.sifinnsynapi.omsorg

import org.springframework.stereotype.Service

@Service
class OmsorgService(
    private val omsorgRepository: OmsorgRepository
) {

    fun harOmsorgen(søkerAktørId: String, pleietrengendeAktørId: String): Boolean =
        omsorgRepository.harOmsorgen(søkerAktørId, pleietrengendeAktørId)
}
