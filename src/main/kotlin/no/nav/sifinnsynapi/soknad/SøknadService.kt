package no.nav.sifinnsynapi.soknad

import no.nav.k9.innsyn.Søknadsammenslåer
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.OppslagsService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Stream


@Service
class SøknadService(
    private val repo: SøknadRepository,
    private val omsorgService: OmsorgService,
    private val oppslagsService: OppslagsService
) {

    @Transactional(readOnly = true)
    fun hentSøknadsopplysningerPerBarn(): List<SøknadDTO> {
        val søkersAktørId =
            (oppslagsService.hentAktørId()
                ?: throw IllegalStateException("Feilet med å hente søkers aktørId.")).aktørId

        return oppslagsService.hentBarn()
            .filter { omsorgService.harOmsorgen(søkerAktørId = søkersAktørId, pleietrengendeAktørId = it.aktørId) }
            .mapNotNull { slåSammenSøknaderFor(søkersAktørId, it.aktørId)?.somSøknadDTO(it) }
    }

    @Transactional(readOnly = true)
    fun slåSammenSøknaderFor(
        søkersAktørId: String,
        barnAktørId: String
    ): Søknad? {
        return repo.hentSøknaderPåPleietrengendeSortertPåOppdatertTidspunkt(barnAktørId)
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

    private fun Søknad.somSøknadDTO(barn: BarnOppslagDTO) = SøknadDTO(
        barn = barn,
        søknad = this
    )

    private fun PsbSøknadDAO.kunPleietrengendeDataFraAndreSøkere(søkerAktørId: String): Søknad {
        val søknad = JsonUtils.fromString(this.søknad, Søknad::class.java)
        return when (this.søkerAktørId) {
            søkerAktørId -> søknad
            else -> Søknadsammenslåer.kunPleietrengendedata(søknad)
        }
    }
}

