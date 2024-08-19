package no.nav.sifinnsynapi.drift

import no.nav.k9.innsyn.Søknadsammenslåer
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.omsorg.OmsorgRepository
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.sak.behandling.BehandlingRepository
import no.nav.sifinnsynapi.soknad.DebugDTO
import no.nav.sifinnsynapi.soknad.PsbSøknadDAO
import no.nav.sifinnsynapi.soknad.SøknadRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Stream


@Service
class DriftService(
    private val søknadRepository: SøknadRepository,
    private val omsorgService: OmsorgService,
    private val behandlingRepository: BehandlingRepository,
    private val omsorgRepository: OmsorgRepository,

    ) {

    private companion object {
        private val logger = LoggerFactory.getLogger(DriftService::class.java)
    }

    @Transactional(readOnly = true)
    fun slåSammenSøknadsopplysningerPerBarn(
        søkerAktørId: String,
        pleietrengendeAktørIder: List<String>,
        ekskluderteSøknadIder: List<String> = emptyList(),
    ): List<DebugDTO> {
        val pleietrengendeAktørIder = omsorgService.hentPleietrengendeSøkerHarOmsorgFor(søkerAktørId)
        if (pleietrengendeAktørIder.isEmpty()) return listOf()

        return pleietrengendeAktørIder
            .mapNotNull { pleietrengendeAktørId ->
                val alleSøknader =
                    søknadRepository.findAllByPleietrengendeAktørIdOrderByOppdatertDatoAsc(pleietrengendeAktørId)
                        .map { it: PsbSøknadDAO -> it.kunPleietrengendeDataFraAndreSøkere(søkerAktørId) }
                        .filter { it: Søknad -> !ekskluderteSøknadIder.contains(it.søknadId.id) }
                        .toList()

                slåSammenSøknaderFor(søkerAktørId, pleietrengendeAktørId, ekskluderteSøknadIder)?.somDebugDTO(
                    pleietrengendeAktørId,
                    alleSøknader
                )
            }
    }

    @Transactional(readOnly = true)
    fun slåSammenSøknaderFor(
        søkersAktørId: String,
        pleietrengendeAktørId: String,
        ekskluderteSøknadIder: List<String>,
    ): Søknad? {
        return søknadRepository.findAllByPleietrengendeAktørIdOrderByOppdatertDatoAsc(pleietrengendeAktørId)
            .use { søknadStream: Stream<PsbSøknadDAO> ->
                søknadStream
                    .map { psbSøknadDAO: PsbSøknadDAO ->
                        psbSøknadDAO.kunPleietrengendeDataFraAndreSøkere(søkersAktørId)
                    }
                    .filter { søknad: Søknad -> !ekskluderteSøknadIder.contains(søknad.søknadId.id) }
                    .reduce(Søknadsammenslåer::slåSammen)
                    .orElse(null)
            }
    }

    private fun Søknad.somDebugDTO(pleietrengendeAktørId: String, alleSøknader: List<Søknad>? = null): DebugDTO {
        return DebugDTO(
            pleietrengendeAktørId = pleietrengendeAktørId,
            søknad = this,
            søknader = alleSøknader
        )
    }

    private fun PsbSøknadDAO.kunPleietrengendeDataFraAndreSøkere(søkerAktørId: String): Søknad {
        val søknad = JsonUtils.fromString(this.søknad, Søknad::class.java)
        return when (this.søkerAktørId) {
            søkerAktørId -> søknad
            else -> Søknadsammenslåer.kunPleietrengendedata(søknad)
        }
    }

    fun oppdaterAktørId(gyldig: String, utgått: String): Int {
        var antallRader = 0
        antallRader += søknadRepository.oppdaterAktørIdForSøker(gyldig, utgått)
        antallRader += behandlingRepository.oppdaterAktørIdForSøker(gyldig, utgått)
        antallRader += omsorgRepository.oppdaterAktørIdForSøker(gyldig, utgått)
        return antallRader
    }
}

