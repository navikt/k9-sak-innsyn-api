package no.nav.sifinnsynapi.soknad

import no.nav.k9.innsyn.Søknadsammenslåer
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.Versjon
import no.nav.k9.søknad.felles.type.Språk
import no.nav.k9.søknad.felles.type.SøknadId
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
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
    fun hentSøknadsopplysningerPerBarn(): List<SøknadDTO> {
        val søkersAktørId =
            (oppslagsService.hentAktørId()
                ?: throw IllegalStateException("Feilet med å hente søkers aktørId.")).aktør_id

        return oppslagsService.hentBarn()
            .filter { omsorgService.harOmsorgen(søkerAktørId = søkersAktørId, pleietrengendeAktørId = it.aktør_id) }
            .mapNotNull { slåSammenSøknaderFor(søkersAktørId, it.aktør_id)?.somSøknadDTO(it) }
    }

    fun slåSammenSøknaderFor(
        søkersAktørId: String,
        barnAktørId: String
    ): Søknad? {
        return repo.hentSøknaderSortertPåOppdatertTidspunkt(søkersAktørId, barnAktørId)
            .use { s ->
                val sammenslåttSøknad =
                    s.map { psbSøknadDAO: PsbSøknadDAO -> psbSøknadDAO.kunPleietrengendeDataFraAndreSøkere(søkersAktørId) }
                        .reduce(Søknadsammenslåer::slåSammen)
                        .orElse(null)

                // TODO: 07/12/2021 Blir det riktig å sette disse påkrevde feltene på denne måten?
                sammenslåttSøknad
                    ?.medSøknadId(SøknadId.of(UUID.randomUUID().toString()))
                    ?.medSpråk(Språk.NORSK_BOKMÅL)
                    ?.medVersjon(Versjon.of("1.0.0"))
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

