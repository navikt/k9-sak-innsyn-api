package no.nav.sifinnsynapi.soknad

import no.nav.k9.innsyn.Søknadsammenslåer
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.OppslagsService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*


@Service
class SøknadService(
    private val repo: SøknadRepository,
    private val omsorgService: OmsorgService,
    private val oppslagsService: OppslagsService
) {

    @Transactional(readOnly = true)
    fun hentSøknadsopplysninger(): Søknad {
        val søkersAktørId =
            (oppslagsService.hentAktørId()
                ?: throw IllegalStateException("Feilet med å hente søkers aktørId.")).aktør_id

        val pleietrengendeAktørIder = oppslagsService.hentBarn().map { it.aktør_id }

        val sammenslåtteSøknader: Optional<Søknad> =
            repo.hentSøknaderSortertPåOppdatertTidspunkt(pleietrengendeAktørIder)
                .map { it.kunPleietrengendeDataFraAndreSøkere(søkersAktørId) }
                .reduce(Søknadsammenslåer::slåSammen)

        return sammenslåtteSøknader.orElseThrow { IllegalStateException("Ingen søknader funnet") }
    }

    private fun PsbSøknadDAO.kunPleietrengendeDataFraAndreSøkere(søkerAktørId: String): Søknad {
        val søknad = JsonUtils.fromString(this.søknad, Søknad::class.java)
        return when (this.søkerAktørId) {
            søkerAktørId -> søknad
            else -> Søknadsammenslåer.kunPleietrengendedata(søknad)
        }
    }
}

