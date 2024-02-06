package no.nav.sifinnsynapi.sak

import no.nav.k9.konstant.Konstant
import org.springframework.stereotype.Service

@Service
class SakService {
    private companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(SakService::class.java)
    }

    fun hentSaker(): List<SakDTO> {
        return listOf()
    }

    fun hentGenerellSaksbehandlingstid(): SaksbehandlingtidDTO {
        val saksbehandlingstidUker = Konstant.FORVENTET_SAKSBEHANDLINGSTID.toHours().div(24 * 7)
        return SaksbehandlingtidDTO(saksbehandlingstidUker = saksbehandlingstidUker)
    }
}
