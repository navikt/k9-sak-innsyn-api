package no.nav.sifinnsynapi.sak.behandling

import no.nav.k9.innsyn.sak.FagsakYtelseType
import org.springframework.stereotype.Service
import java.util.stream.Stream

@Service
class BehandlingService(private val behandlingRepository: BehandlingRepository) {
    fun lagreBehandling(behandling: BehandlingDAO) {
        behandlingRepository.save(behandling)
    }

    fun hentBehandlinger(søkerAktørId: String, pleietrengendeAktørId: String, ytelsetype: FagsakYtelseType): Stream<BehandlingDAO> {
        return behandlingRepository.findAllBySøkerAktørIdAndPleietrengendeAktørIdAndYtelsetypeOrderByOppdatertDatoAsc(søkerAktørId, pleietrengendeAktørId, ytelsetype)
    }
}
