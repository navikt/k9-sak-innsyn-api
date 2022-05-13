package no.nav.sifinnsynapi.soknad

import no.nav.k9.innsyn.Søknadsammenslåer
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.HentIdenterResultat
import no.nav.sifinnsynapi.oppslag.IdentGruppe
import no.nav.sifinnsynapi.oppslag.IdentInformasjon
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

        val pleietrengendeAktørIder = omsorgService.hentPleietrengendeSøkerHarOmsorgFor(søkersAktørId)
        if (pleietrengendeAktørIder.isEmpty()) return listOf()

        // Hent folkeregistrert ident for alle pleietrengende aktørIder...
        val identer = oppslagsService.hentIdenter(
            identer = pleietrengendeAktørIder,
            identGrupper = listOf(IdentGruppe.FOLKEREGISTERIDENT)
        )

        return pleietrengendeAktørIder
            .mapNotNull { pleietrengendeAktørId ->
                val identInformasjon = hentIdentInformasjonForPleietrengendeAktørId(identer, pleietrengendeAktørId)
                slåSammenSøknaderFor(søkersAktørId, pleietrengendeAktørId)?.somSøknadDTO(identInformasjon.ident)
            }
    }

    fun hentIdentInformasjonForPleietrengendeAktørId(
        hentIdenterResultat: List<HentIdenterResultat>,
        pleietrengendeAktørId: String
    ): IdentInformasjon {
        return hentIdenterResultat
            .first { it.ident == pleietrengendeAktørId }.identer
            .first { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }
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

    private fun Søknad.somSøknadDTO(barnFolkeregistrertIdentGruppe: String) = SøknadDTO(
        barnFolkeregistrertIdent = barnFolkeregistrertIdentGruppe,
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

