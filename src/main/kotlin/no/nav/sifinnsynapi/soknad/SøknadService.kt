package no.nav.sifinnsynapi.soknad

import no.nav.k9.innsyn.Søknadsammenslåer
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.omsorg.OmsorgService
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
            .map { it.aktør_id }
            .filter { omsorgService.harOmsorgen(søkerAktørId = søkersAktørId, pleietrengendeAktørId = it) }
            .mapNotNull { slåSammenSøknaderFor(søkersAktørId, it) }
    }

    fun slåSammenSøknaderFor(
        søkersAktørId: String,
        pleietrengendeAktørId: String
    ): SøknadDTO? {
        val stream = repo.hentSøknaderSortertPåOppdatertTidspunkt(søkersAktørId, pleietrengendeAktørId)
        val søknadDTO = stream.use { s ->
            s.map { psbSøknadDAO: PsbSøknadDAO -> psbSøknadDAO.kunPleietrengendeDataFraAndreSøkere(søkersAktørId) }
                .reduce(Søknadsammenslåer::slåSammen)
                .orElse(null)
                ?.somSøknadDTO(pleietrengendeAktørId)
        }

        return søknadDTO
    }

    fun Søknad.somSøknadDTO(pleietrengendeAktørId: String) = SøknadDTO(
        pleietrengendeAktørId = pleietrengendeAktørId,
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

