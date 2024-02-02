package no.nav.sifinnsynapi.sak

import no.nav.k9.konstant.Konstant
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.OppslagsService
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SakService(
    private val oppslagsService: OppslagsService,
    private val omsorgService: OmsorgService,
) {
    private companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(SakService::class.java)
    }

    fun hentSaker(): List<SakDTO> {
        val søkersAktørId =
            (oppslagsService.hentAktørId()
                ?: throw IllegalStateException("Feilet med å hente søkers aktørId.")).aktørId

        // TODO Henter saker der søker har omsorgen for pleietrengende
        val barnOppslagDTOS: List<BarnOppslagDTO> = oppslagsService.hentBarn()
        val pleietrengendeSøkerHarOmsorgenFor = omsorgService.hentPleietrengendeSøkerHarOmsorgFor(søkersAktørId)
        if (pleietrengendeSøkerHarOmsorgenFor.isEmpty()) {
            logger.info("Fant ingen pleietrengende søker har omsorgen for.")
            return listOf()
        }
        logger.info("Fant {} pleietrengende søker har omsorgen for.", pleietrengendeSøkerHarOmsorgenFor.size)

        // TODO: Hent lagrede saker på pleietrengende

        // TODO: Map til SakDTO
        return listOf(
            SakDTO(
                saksbehandlingsFrist = LocalDate.now().plusDays(Konstant.FORVENTET_SAKSBEHANDLINGSTID.toDays())
            )
        )
    }

    fun hentGenerellSaksbehandlingstid(): SaksbehandlingtidDTO {
        val saksbehandlingstidUker = Konstant.FORVENTET_SAKSBEHANDLINGSTID.toHours().div(24* 7)
        return SaksbehandlingtidDTO(saksbehandlingstidUker = saksbehandlingstidUker)
    }
}
