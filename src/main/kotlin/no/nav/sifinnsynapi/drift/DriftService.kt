package no.nav.sifinnsynapi.drift

import no.nav.k9.innsyn.Søknadsammenslåer
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.soknad.DebugDTO
import no.nav.sifinnsynapi.soknad.PsbSøknadDAO
import no.nav.sifinnsynapi.soknad.SøknadRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Stream


@Service
class DriftService(
    private val repo: SøknadRepository,
    private val omsorgService: OmsorgService,
    private val oppslagsService: OppslagsService,
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(DriftService::class.java)
    }

    @Transactional(readOnly = true)
    fun slåSammenSøknadsopplysningerPerBarn(
        søkerAktørId: String,
        pleietrengendeAktørIder: List<String>,
    ): List<DebugDTO> {
        val pleietrengendeAktørIder = omsorgService.hentPleietrengendeSøkerHarOmsorgFor(søkerAktørId)
        if (pleietrengendeAktørIder.isEmpty()) return listOf()

        return pleietrengendeAktørIder
            .mapNotNull { pleietrengendeAktørId ->
                val alleSøknader =
                    repo.findAllByPleietrengendeAktørIdOrderByOppdatertDatoAsc(pleietrengendeAktørId)
                        .map { JsonUtils.fromString(it.søknad, Søknad::class.java) }
                        .toList()
                slåSammenSøknaderFor(søkerAktørId, pleietrengendeAktørId)?.somDebugDTO(pleietrengendeAktørId, alleSøknader)
            }
    }

    @Transactional(readOnly = true)
    fun slåSammenSøknaderFor(
        søkersAktørId: String,
        pleietrengendeAktørId: String,
    ): Søknad? {
        return repo.findAllByPleietrengendeAktørIdOrderByOppdatertDatoAsc(pleietrengendeAktørId)
            .use { søknadStream: Stream<PsbSøknadDAO> ->
                søknadStream.map { psbSøknadDAO: PsbSøknadDAO ->
                    psbSøknadDAO.kunPleietrengendeDataFraAndreSøkere(søkersAktørId)
                }
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
}

