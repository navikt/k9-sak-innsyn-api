package no.nav.sifinnsynapi.soknad

import no.nav.k9.innsyn.Søknadsammenslåer
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.OppslagsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Stream


@Service
class SøknadService(
    private val repo: SøknadRepository,
    private val omsorgService: OmsorgService,
    private val oppslagsService: OppslagsService,
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(SøknadService::class.java)
    }

    fun hentSøknad(journalpostId: String): PsbSøknadDAO? {
        return repo.findById(journalpostId).orElse(null)
    }

    @Transactional(readOnly = true)
    fun slåSammenSøknadsopplysningerPerBarn(): List<SøknadDTO> {
        val søkersAktørId =
            (oppslagsService.hentAktørId()
                ?: throw IllegalStateException("Feilet med å hente søkers aktørId.")).aktørId

        val barnOppslagDTOS: List<BarnOppslagDTO> = oppslagsService.hentBarn()
        if (barnOppslagDTOS.isEmpty()) {
            logger.info("Fant ingen barn på søker")
            return listOf( )
        }

        val pleietrengendeAktørIder = omsorgService.hentPleietrengendeSøkerHarOmsorgFor(søkersAktørId)
        if (pleietrengendeAktørIder.isEmpty()) {
            logger.info("Fant ingen pleietrengende søker har omsorgen for.")
            return listOf()
        }
        logger.info("Fant {} pleietrengende søker har omsorgen for.", pleietrengendeAktørIder.size)

        return pleietrengendeAktørIder
            .mapNotNull { pleietrengendeAktørId ->
                val barn = barnOppslagDTOS.firstOrNull { it.aktørId == pleietrengendeAktørId }
                if (barn != null) {
                    slåSammenSøknaderFor(søkersAktørId, pleietrengendeAktørId)?.somSøknadDTO(barn)
                } else {
                    logger.info("PleietrengedeAktørId matchet ikke med aktørId på barn fra oppslag.")
                    null
                }
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

    fun lagreSøknad(søknad: PsbSøknadDAO): PsbSøknadDAO = repo.save(søknad)

    @Transactional
    fun trekkSøknad(journalpostId: String): Boolean {
        repo.deleteById(journalpostId)
        return !repo.existsById(journalpostId)
    }

    private fun Søknad.somSøknadDTO(barn: BarnOppslagDTO, alleSøknader: List<Søknad>? = null): SøknadDTO {
        return SøknadDTO(
            barn = barn,
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

