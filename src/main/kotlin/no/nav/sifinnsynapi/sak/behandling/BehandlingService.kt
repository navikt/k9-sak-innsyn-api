package no.nav.sifinnsynapi.sak.behandling

import org.springframework.stereotype.Service
import java.util.stream.Stream

@Service
class BehandlingService(private val behandlingRepository: BehandlingRepository) {
    fun lagreBehandling(behandling: BehandlingDAO) {
        behandlingRepository.save(behandling)
    }

    fun hentBehandlinger(pleietrengendeAktørId: String): Stream<BehandlingDAO> {
        return behandlingRepository.findAllByPleietrengendeAktørIdOrderByOppdatertDatoAsc(pleietrengendeAktørId)
    }
}
