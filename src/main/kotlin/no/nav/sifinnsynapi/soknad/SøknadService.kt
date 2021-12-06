package no.nav.sifinnsynapi.soknad

import no.nav.k9.innsyn.Søknadsammenslåer
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.OppslagsService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


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
                ?: throw IllegalStateException("Feilet med å hente søkers aktørId.")).aktør_id

        return oppslagsService.hentBarn()
            .filter { omsorgService.harOmsorgen(søkerAktørId = søkersAktørId, pleietrengendeAktørId = it.aktør_id) }
            .mapNotNull { slåSammenSøknaderFor(søkersAktørId, it) }
    }

    fun slåSammenSøknaderFor(
        søkersAktørId: String,
        barn: BarnOppslagDTO
    ): SøknadDTO? {
        return repo.hentSøknaderSortertPåOppdatertTidspunkt(søkersAktørId, barn.aktør_id).use { s ->
            s.map { psbSøknadDAO: PsbSøknadDAO -> psbSøknadDAO.kunPleietrengendeDataFraAndreSøkere(søkersAktørId) }
                .reduce(Søknadsammenslåer::slåSammen)
                .orElse(null)
                ?.somSøknadDTO(barn)
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

