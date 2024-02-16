package no.nav.sifinnsynapi.sak

import jakarta.transaction.Transactional
import no.nav.k9.innsyn.sak.Aksjonspunkt
import no.nav.k9.innsyn.sak.Behandling
import no.nav.k9.innsyn.sak.BehandlingStatus
import no.nav.k9.innsyn.sak.SøknadInfo
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.konstant.Konstant
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.Kildesystem
import no.nav.sifinnsynapi.dokumentoversikt.DokumentService
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.LegacyInnsynApiService
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.LegacySøknadstype
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.sak.behandling.BehandlingDAO
import no.nav.sifinnsynapi.sak.behandling.BehandlingService
import no.nav.sifinnsynapi.soknad.SøknadService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.*
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

@Service
class SakService(
    private val behandlingService: BehandlingService,
    private val dokumentService: DokumentService,
    private val oppslagsService: OppslagsService,
    private val omsorgService: OmsorgService,
    private val søknadService: SøknadService,
    private val legacyInnsynApiService: LegacyInnsynApiService,
) {
    private companion object {
        private val logger = LoggerFactory.getLogger(SakService::class.java)
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
        val oppslagsbarn = oppslagsService.hentBarn()
        logger.info("Fant ${oppslagsbarn.size} barn i folkeregisteret registrert på søker.")

        val pleietrengendeMedBehandlinger = oppslagsbarn
            .map { it.somPleietrengendeDTO() }
            .assosierPleietrengendeMedBehandlinger(søkersAktørId, fagsakYtelseType)

        val søkersDokmentoversikt = dokumentService.hentDokumentOversikt()
        logger.info("Fant ${søkersDokmentoversikt.size} dokumenter i søkers dokumentoversikt.")

        // Returnerer hver pleietrengende med tilhørende sak, behandlinger, søknader og dokumenter.
        return pleietrengendeMedBehandlinger
            .mapNotNull { (pleietrengendeDTO, behandlinger) ->

                // Alle behandlinger har samme saksnummer og fagsakYtelseType for pleietrengende
                behandlinger.firstOrNull()?.let { behandling: Behandling ->
                    PleietrengendeMedSak(
                        pleietrengende = pleietrengendeDTO,
                        sak = SakDTO(
                            saksnummer = behandling.fagsak.saksnummer, // Alle behandlinger har samme saksnummer for pleietrengende
                            fagsakYtelseType = behandling.fagsak.ytelseType, // Alle behandlinger har samme fagsakYtelseType for pleietrengende

                            // Utleder sakbehandlingsfrist fra åpen behandling. Dersom det ikke finnes en åpen behandling, returneres null.
                            saksbehandlingsFrist = behandlinger.utledSaksbehandlingsfristFraÅpenBehandling(),

                            behandlinger = behandlinger.behandlingerMedTilhørendeSøknader(søkersDokmentoversikt)
                        )
                    )
                }
            }
    }

    private fun List<Behandling>.utledSaksbehandlingsfristFraÅpenBehandling(): LocalDate? {
        val åpenBehandling = firstOrNull { it.status != BehandlingStatus.AVSLUTTET }
        return åpenBehandling?.utledSaksbehandlingsfrist(null)?.getOrNull()?.toLocalDate()
    }

    private fun MutableList<Behandling>.behandlingerMedTilhørendeSøknader(søkersDokmentoversikt: List<DokumentDTO>): List<BehandlingDTO> =
        map { behandling ->

            val søknaderISak: List<SøknaderISakDTO> = behandling.søknader
                .medTilhørendeDokumenter(søkersDokmentoversikt)
                .filterNot { (søknad, _) -> søknad.hentOgMapTilK9FormatSøknad() == null } // Filtrer bort søknader som ikke finnes
                .map { (søknad, dokumenter) ->
                    val k9FormatSøknad =
                        søknad.hentOgMapTilK9FormatSøknad()!!  // verifisert at søknad finnes ovenfor

                    val søknadId = k9FormatSøknad.søknadId.id
                    val søknadsType = utledSøknadsType(k9FormatSøknad, søknadId)

                    SøknaderISakDTO(
                        søknadId = UUID.fromString(søknadId),
                        søknadstype = søknadsType,
                        k9FormatSøknad = k9FormatSøknad,
                        dokumenter = dokumenter
                    )
                }

            BehandlingDTO(
                status = behandling.status,
                opprettetTidspunkt = behandling.opprettetTidspunkt,
                avsluttetTidspunkt = behandling.avsluttetTidspunkt,
                søknader = søknaderISak,
                aksjonspunkter = behandling.aksjonspunkter.somAksjonspunktDTO()
            )
        }

    private fun utledSøknadsType(
        k9FormatSøknad: Søknad,
        søknadId: String,
    ): Søknadstype = when (val ks = k9FormatSøknad.kildesystem.getOrNull()) {
        null -> {
            logger.info("Fant ingen kildesystem for søknad med søknadId $søknadId.")
            val legacySøknad = kotlin.runCatching { legacyInnsynApiService.hentLegacySøknad(søknadId) }.getOrNull()
            if (legacySøknad == null) {
                logger.warn("Fant ingen legacy søknad for søknad med søknadId $søknadId og kunne ikke utlede søknadstype. Returnerer ukjent.")
                Søknadstype.UKJENT
            } else when (legacySøknad.søknadstype) {
                LegacySøknadstype.PP_SYKT_BARN -> Søknadstype.SØKNAD
                LegacySøknadstype.PP_ETTERSENDELSE -> Søknadstype.ETTERSENDELSE
                LegacySøknadstype.PP_LIVETS_SLUTTFASE_ETTERSENDELSE -> Søknadstype.ETTERSENDELSE
                LegacySøknadstype.OMS_ETTERSENDELSE -> Søknadstype.ETTERSENDELSE
                LegacySøknadstype.PP_SYKT_BARN_ENDRINGSMELDING -> Søknadstype.ENDRINGSMELDING
            }
        }

        Kildesystem.ENDRINGSDIALOG -> Søknadstype.ENDRINGSMELDING
        Kildesystem.SØKNADSDIALOG -> Søknadstype.SØKNAD
        Kildesystem.PUNSJ -> Søknadstype.SØKNAD // TODO: Blir dette riktig?
        Kildesystem.UTLEDET -> Søknadstype.SØKNAD // // TODO: Blir dette riktig?

        else -> throw error("Ukjent kildesystem $ks")
    }

    fun hentGenerellSaksbehandlingstid(): SaksbehandlingtidDTO {
        val saksbehandlingstidUker = Konstant.FORVENTET_SAKSBEHANDLINGSTID.days.div(7L)
        return SaksbehandlingtidDTO(saksbehandlingstidUker = saksbehandlingstidUker)
    }

    private fun MutableSet<SøknadInfo>.medTilhørendeDokumenter(søkersDokmentoversikt: List<DokumentDTO>) =
        associateWith { søknadInfo: SøknadInfo ->
            val dokumenterTilknyttetSøknad =
                søkersDokmentoversikt.filter { dokument -> dokument.journalpostId == søknadInfo.journalpostId }
            logger.info("Fant ${dokumenterTilknyttetSøknad.size} dokumenter knyttet til søknaden med journalpostId ${søknadInfo.journalpostId}.")
            dokumenterTilknyttetSøknad
        }

    private fun List<PleietrengendeDTO>.assosierPleietrengendeMedBehandlinger(søkerAktørId: String, fagsakYtelseType: FagsakYtelseType) =
        associateWith { pleietrengendeDTO ->
            val behandlinger = behandlingService.hentBehandlinger(søkerAktørId, pleietrengendeDTO.aktørId, fagsakYtelseType)
                .somBehandling()
                .toList()
            logger.info("Fant ${behandlinger.size} behandlinger for pleietrengende.")
            behandlinger
        }

    private fun SøknadInfo.hentOgMapTilK9FormatSøknad(): Søknad? = søknadService.hentSøknad(journalpostId)
        ?.let { JsonUtils.fromString(it.søknad, Søknad::class.java) }

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
