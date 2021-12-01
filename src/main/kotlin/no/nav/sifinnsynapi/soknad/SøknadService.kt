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


        val pleietrengendeAktørIder = oppslagsService.hentBarn()
            .map { it.aktør_id }
            .filter { pleietrengendeAktørId: String ->
                omsorgService.harOmsorgen(søkerAktørId = søkersAktørId, pleietrengendeAktørId = pleietrengendeAktørId)
            }
        /*.mapNotNull { pleietrengendeAktørId: String ->
                slåSammenSøknaderFor(søkersAktørId, pleietrengendeAktørId)?.let { SøknadDTO(pleietrengendeAktørId, it) }
            }*/

        val s1 = slåSammenSøknaderFor(søkersAktørId, pleietrengendeAktørIder[0])!!
        val s2 = slåSammenSøknaderFor(søkersAktørId, pleietrengendeAktørIder[1])!!
        return listOf(s1, s2)

    }

    @Transactional(readOnly = true)
    fun slåSammenSøknaderFor(
        søkersAktørId: String,
        pleietrengendeAktørId: String
    ): SøknadDTO? {
        return repo.hentSøknaderSortertPåOppdatertTidspunkt(søkersAktørId, pleietrengendeAktørId)
            .map { psbSøknadDAO: PsbSøknadDAO -> psbSøknadDAO.kunPleietrengendeDataFraAndreSøkere(søkersAktørId) }
            .reduce(Søknadsammenslåer::slåSammen)
            .orElse(null)
            ?.somSøknadDTO(pleietrengendeAktørId)
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

