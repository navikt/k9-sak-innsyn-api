package no.nav.sifinnsynapi.sak

import jakarta.transaction.Transactional
import no.nav.k9.innsyn.sak.Aksjonspunkt
import no.nav.k9.innsyn.sak.Behandling
import no.nav.k9.innsyn.sak.SøknadInfo
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.konstant.Konstant
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.dokumentoversikt.DokumentService
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.sak.behandling.BehandlingDAO
import no.nav.sifinnsynapi.sak.behandling.BehandlingService
import no.nav.sifinnsynapi.soknad.SøknadService
import org.springframework.stereotype.Service
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

@Service
class SakService(
    private val behandlingService: BehandlingService,
    private val dokumentService: DokumentService,
    private val oppslagsService: OppslagsService,
    private val omsorgService: OmsorgService,
    private val søknadService: SøknadService,
) {
    private companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(SakService::class.java)
    }

    @Transactional
    fun hentSaker(fagsakYtelseType: FagsakYtelseType): List<PleietrengendeMedSak> {
        val søkersAktørId = oppslagsService.hentAktørId()?.aktørId
            ?: throw IllegalStateException("Feilet med å hente søkers aktørId.")

        val pleietrengendeSøkerHarOmsorgFor = omsorgService.hentPleietrengendeSøkerHarOmsorgFor(søkersAktørId)

        // Returner tom liste hvis søker ikke har omsorg for noen pleietrengende.
        if (pleietrengendeSøkerHarOmsorgFor.isEmpty()) {
            logger.info("Fant ingen pleietrengende søker har omsorgen for.")
            return emptyList()
        }
        logger.info("Fant ${pleietrengendeSøkerHarOmsorgFor.size} pleietrengende søker har omsorgen for.")

        // Slå sammen pleietrengende og behandlinger
        val pleietrengendesBehandlinger = oppslagsService.hentBarn()
            .map { it.somPleietrengendeDTO() }
            .assosierPleietrengendeMedBehandlinger(fagsakYtelseType)

        // Returnerer pleietrengende med tilhørende sak, behandlinger, søknader og dokumenter
        return pleietrengendesBehandlinger.mapNotNull { (pleietrengendeDTO, behandlinger) ->
            behandlinger.firstOrNull()
                ?.let { førsteBehandling: Behandling -> // Alle behandlinger har samme saksnummer og fagsakYtelseType
                    PleietrengendeMedSak(
                        pleietrengende = pleietrengendeDTO,
                        sak = SakDTO(
                            saksnummer = førsteBehandling.fagsak.saksnummer, // Alle behandlinger har samme saksnummer for pleietrengende

                            saksbehandlingsFrist = førsteBehandling.utledSaksbehandlingsfrist(null)
                                .getOrNull()
                                ?.toLocalDate(),

                            fagsakYtelseType = førsteBehandling.fagsak.ytelseType, // Alle behandlinger har samme fagsakYtelseType for pleietrengende

                            behandlinger = behandlinger.map { behandling ->
                                BehandlingDTO(
                                    status = behandling.status,
                                    søknader = behandling.søknader
                                        .hentOgMapTilK9FormatSøknad()
                                        .hentDokumenterOgMapTilSøknaderISakDTO(),
                                    aksjonspunkter = behandling.aksjonspunkter.somAksjonspunktDTO()
                                )
                            }
                        )
                    )
                }
        }
    }

    fun hentGenerellSaksbehandlingstid(): SaksbehandlingtidDTO {
        val saksbehandlingstidUker = Konstant.FORVENTET_SAKSBEHANDLINGSTID.toHours().div(24 * 7)
        return SaksbehandlingtidDTO(saksbehandlingstidUker = saksbehandlingstidUker)
    }

    private fun List<PleietrengendeDTO>.assosierPleietrengendeMedBehandlinger(fagsakYtelseType: FagsakYtelseType) = associateWith { pleietrengendeDTO ->
        behandlingService.hentBehandlinger(pleietrengendeDTO.aktørId, fagsakYtelseType)
            .somBehandling()
            .toList()
    }

    private fun Set<SøknadInfo>.hentOgMapTilK9FormatSøknad(): List<Søknad> =
        mapNotNull { søknad -> // Filtrer bort søknader som ikke finnes
            søknadService.hentSøknad(søknad.journalpostId)
                ?.let { JsonUtils.fromString(it.søknad, Søknad::class.java) }
        }

    private fun List<Søknad>.hentDokumenterOgMapTilSøknaderISakDTO(): List<SøknaderISakDTO> =
        map { søknad ->
            SøknaderISakDTO(
                k9FormatSøknad = søknad,
                kildesystem = søknad.kildesystem.getOrNull(),
                dokumenter = emptyList() // TODO: Hent dokumenter
            )
        }

    private fun BarnOppslagDTO.somPleietrengendeDTO() = PleietrengendeDTO(
        identitetsnummer = this.identitetsnummer!!,
        fødselsdato = this.fødselsdato,
        fornavn = this.fornavn,
        mellomnavn = this.mellomnavn,
        etternavn = this.etternavn,
        aktørId = this.aktørId
    )

    private fun Set<Aksjonspunkt>.somAksjonspunktDTO(): List<AksjonspunktDTO> =
        map { AksjonspunktDTO(venteårsak = it.venteårsak) }

    private fun Stream<BehandlingDAO>.somBehandling(): Stream<Behandling> =
        map { JsonUtils.fromString(it.behandling, Behandling::class.java) }
}
