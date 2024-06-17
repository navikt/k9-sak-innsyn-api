package no.nav.sifinnsynapi.sak

import jakarta.transaction.Transactional
import no.nav.k9.ettersendelse.Ettersendelse
import no.nav.k9.innsyn.sak.*
import no.nav.k9.konstant.Konstant
import no.nav.k9.søknad.Innsending
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.Kildesystem
import no.nav.k9.søknad.ytelse.psb.v1.PleiepengerSyktBarn
import no.nav.sifinnsynapi.dokumentoversikt.DokumentService
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.LegacyInnsynApiService
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.LegacySøknadDTO
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.LegacySøknadstype
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.HentBarnForespørsel
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.oppslag.Organisasjon
import no.nav.sifinnsynapi.sak.behandling.BehandlingDAO
import no.nav.sifinnsynapi.sak.behandling.BehandlingService
import no.nav.sifinnsynapi.sak.behandling.SaksbehandlingstidUtleder
import no.nav.sifinnsynapi.soknad.InnsendingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.*
import java.util.function.Supplier
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

@Service
class SakService(
    private val behandlingService: BehandlingService,
    private val dokumentService: DokumentService,
    private val oppslagsService: OppslagsService,
    private val omsorgService: OmsorgService,
    private val innsendingService: InnsendingService,
    private val legacyInnsynApiService: LegacyInnsynApiService,
) {
    private companion object {
        private val logger = LoggerFactory.getLogger(SakService::class.java)
    }

    @Transactional
    fun hentSaker(fagsakYtelseType: FagsakYtelseType): List<PleietrengendeMedSak> {
        val søker = oppslagsService.hentSøker()
            ?: throw IllegalStateException("Feilet med å hente søker.")

        val pleietrengendeSøkerHarOmsorgFor = omsorgService.hentPleietrengendeSøkerHarOmsorgFor(søker.aktørId)

        val behandlingerSupplier = Supplier<Stream<BehandlingDAO>> {
            behandlingService.hentBehandlinger(søker.aktørId, fagsakYtelseType)
        }

        val pleietrengendeMedBehandlinger = behandlingerSupplier.get()
            .map { it.pleietrengendeAktørId } // Henter ut alle pleietrengende aktørIder
            .distinct() // Fjerner duplikater
            .toList()
            .let { pleietrengendeAktørIder ->
                // Henter pleietrengende basert på aktørIder
                logger.info("Henter ${pleietrengendeAktørIder.size} pleietrengende som søker har behandlinger for.")
                oppslagsService.systemoppslagBarn(HentBarnForespørsel(identer = pleietrengendeAktørIder))
                    .map { it.somPleietrengendeDTO(pleietrengendeSøkerHarOmsorgFor) }
            }
            .assosierPleietrengendeMedBehandlinger(behandlingerSupplier)

        if (pleietrengendeMedBehandlinger.isEmpty() && behandlingerSupplier.get().count() > 0) {
            loggNyesteBehandling(
                "Pleietrengende med behandlinger var tomt, men søker hadde behandlinger",
                behandlingerSupplier
            )
            return emptyList()
        }

        val søkersDokmentoversikt = dokumentService.hentDokumentOversikt()
        logger.info("Fant ${søkersDokmentoversikt.size} dokumenter i søkers dokumentoversikt.")

        // Returnerer hver pleietrengende med tilhørende sak, behandlinger, søknader og dokumenter.
        val antallSaker = pleietrengendeMedBehandlinger
            .mapNotNull { (pleietrengendeDTO, behandlinger) ->

                // Alle behandlinger har samme saksnummer og fagsakYtelseType for pleietrengende
                behandlinger.firstOrNull()?.let { behandling: Behandling ->
                    val fagsak = behandling.fagsak
                    val ytelseType = fagsak.ytelseType
                    logger.info("Behandlinger som inngår fagsak har saksnummer ${fagsak.saksnummer} og ytelseType $ytelseType.")

                    val behandlingerMedTilhørendeInnsendelser =
                        behandlinger.behandlingerMedTilhørendeInnsendelser(søkersDokmentoversikt)

                    if (behandlingerMedTilhørendeInnsendelser.isEmpty()) {
                        logger.info("Ignorerer fagsak ${fagsak.saksnummer.verdi} fordi vi ikke hadde noen behandlinger å vise.")
                        return@mapNotNull null
                    }

                    val saksbehandlingsFrist = behandlinger.utledSaksbehandlingsfristFraÅpenBehandling()
                    val utledetStatus = utledStatus(behandlingerMedTilhørendeInnsendelser, saksbehandlingsFrist)

                    PleietrengendeMedSak(
                        pleietrengende = pleietrengendeDTO,
                        sak = SakDTO(
                            saksnummer = fagsak.saksnummer, // Alle behandlinger har samme saksnummer for pleietrengende
                            utledetStatus = utledetStatus,
                            fagsakYtelseType = no.nav.k9.kodeverk.behandling.FagsakYtelseType.fraKode(ytelseType.kode), // Alle behandlinger har samme fagsakYtelseType for pleietrengende
                            ytelseType = ytelseType, // Alle behandlinger har samme fagsakYtelseType for pleietrengende
                            // Utleder sakbehandlingsfrist fra åpen behandling. Dersom det ikke finnes en åpen behandling, returneres null.
                            saksbehandlingsFrist = saksbehandlingsFrist,
                            behandlinger = behandlingerMedTilhørendeInnsendelser
                        )
                    )
                }
            }
        logger.info(
            "Fant ${antallSaker.size} saker med {} behandlinger.",
            antallSaker.flatMap { it.sak.behandlinger }.size
        )
        return antallSaker
    }

    private fun utledStatus(
        behandlinger: List<BehandlingDTO>,
        saksbehandlingsFrist: LocalDate?,
    ): UtledetStatus {
        val sisteBehandling = behandlinger.sortedByDescending { it.opprettetTidspunkt }.first()
        val innsendelser = sisteBehandling.innsendelser.sortedByDescending { it.mottattTidspunkt }

        val inneholderKunEttersendelser = innsendelser.all { it.innsendelsestype == Innsendelsestype.ETTERSENDELSE }
        val sisteInnsendelseErEttersendelse = innsendelser.firstOrNull { it.innsendelsestype == Innsendelsestype.ETTERSENDELSE } != null
        val behandlingStatus = when {
            // Dersom behandlingene kun inneholder ettersendelser eller siste innsendelse er ettersendelse, settes status til AVSLUTTET.
            inneholderKunEttersendelser || sisteInnsendelseErEttersendelse -> BehandlingStatus.AVSLUTTET

            // Ellers settes status til status på siste behandling.
            else -> sisteBehandling.status
        }
        return UtledetStatus(
            status = behandlingStatus,
            aksjonspunkter = sisteBehandling.aksjonspunkter,
            saksbehandlingsFrist = saksbehandlingsFrist
        )
    }

    private fun loggNyesteBehandling(prefix: String, behandlingerSupplier: Supplier<Stream<BehandlingDAO>>) {
        val behandlinger = behandlingerSupplier.get()
        val nyesteSak = behandlinger.somBehandling().findFirst().getOrNull()
        logger.info("$prefix. Søker har {} behandlinger og nyeste saksnummer={} med status={} og venteårsaker={}",
            behandlinger.count(),
            nyesteSak?.fagsak?.saksnummer?.verdi,
            nyesteSak?.status,
            nyesteSak?.aksjonspunkter?.joinToString { it.venteårsak.name }
        )
    }

    private fun List<Behandling>.utledSaksbehandlingsfristFraÅpenBehandling(): LocalDate? {
        val åpenBehandling = firstOrNull { it.status != BehandlingStatus.AVSLUTTET }
        return åpenBehandling?.let { SaksbehandlingstidUtleder.utled(it) }?.toLocalDate()
    }

    private fun MutableList<Behandling>.behandlingerMedTilhørendeInnsendelser(søkersDokmentoversikt: List<DokumentDTO>): List<BehandlingDTO> =
        mapNotNull { behandling ->
            logger.info("Henter og mapper søknader i behandling med behandlingsId ${behandling.behandlingsId}.")
            if (skalIgnorereBehandling(behandling, søkersDokmentoversikt)) {
                return@mapNotNull null
            }
            mapBehandling(behandling, søkersDokmentoversikt)
        }

    private fun skalIgnorereBehandling(
        behandling: Behandling,
        søkersDokmentoversikt: List<DokumentDTO>,
    ): Boolean {
        if (behandling.status == BehandlingStatus.AVSLUTTET) {
            return false
        }
        val behandlingsId = behandling.behandlingsId
        val saksnummer = behandling.fagsak.saksnummer

        if (behandling.innsendinger.isEmpty()) {
            logger.info("Ignorerer behandling={} for sak={} fordi søknader er tom", behandlingsId, saksnummer)
            return true
        }
        if (behandling.innsendinger.all { it.kildesystem == Kildesystem.PUNSJ }) {
            logger.info(
                "Ignorerer behandling={} for sak={} fordi søknader innholder kun punsj",
                behandlingsId,
                saksnummer
            )
            return true
        }

        if (søkersDokmentoversikt.none { dok -> behandling.innsendinger.any { s -> dok.journalpostId == s.journalpostId } }) {
            logger.info(
                "Ignorerer behandling={} for sak={} fordi søknader innholder ingen støttet dokument fra dokumentoversikt. " +
                        "Sannsynligvis skyldes det at søknad innholder kun punsj, men før kildesystem ble innført ",
                behandlingsId,
                saksnummer
            )
            return true
        }

        return false
    }

    private fun mapBehandling(
        behandling: Behandling,
        søkersDokmentoversikt: List<DokumentDTO>,
    ): BehandlingDTO {
        val innsendelserISak: List<InnsendelserISakDTO> = behandling.innsendinger
            .medTilhørendeDokumenter(søkersDokmentoversikt)
            .medTilhørendeInnsendelser(søkersDokmentoversikt)
            .requireNoNulls() // Kaster exception hvis noen søknader er null.


        val utgåendeDokumenterISaken = søkersDokmentoversikt
            // TODO: Filtrerer på dokumenter som har matchende journalpostId med behandlingen og er utgående for å koble dokumenter til behandlingen.
            .filter { it.journalposttype == Journalposttype.UTGÅENDE }

        return BehandlingDTO(
            status = behandling.status,
            opprettetTidspunkt = behandling.opprettetTidspunkt,
            avsluttetTidspunkt = behandling.avsluttetTidspunkt,
            innsendelser = innsendelserISak,
            utgåendeDokumenter = utgåendeDokumenterISaken,
            aksjonspunkter = behandling.aksjonspunkter.somAksjonspunktDTO()
        )
    }

    private fun Innsending.skalIgnorereInnsendelse(
        innsendingInfo: InnsendingInfo,
        søkersDokmentoversikt: List<DokumentDTO>,
    ): Boolean {
        return when {
            // Dersom innsendingen er en søknad og kildesystem er punsj, skal innsendingen ignoreres.
            this is Søknad && this.kildesystem == Kildesystem.PUNSJ -> {
                logger.info("Ignorerer innsending(${innsendingInfo.type}) med journalpostId=${innsendingInfo.journalpostId} fordi den er fra punsj.")
                true
            }

            // Dersom innsendingen ikke finnes i søkers dokumentoversikt, skal innsendingen ignoreres.
            !søkersDokmentoversikt.inneholder(innsendingInfo) -> {
                logger.info("Ignorerer innsending(${innsendingInfo.type}) med søknadId=$søknadId fordi den ikke finnes i søkers dokumentoversikt.")
                true
            }

            /*this is Ettersendelse -> { //Deaktivert til ettersendelse går i prod.
                logger.info("Ignorerer innsending(${innsendingInfo.type}) med journalpostId=${innsendingInfo.journalpostId} fordi ettersendelse er ikke aktivert i prod.")
                true
            }*/

            else -> false
        }
    }

    private fun Map<InnsendingInfo, List<DokumentDTO>>.medTilhørendeInnsendelser(søkersDokmentoversikt: List<DokumentDTO>): List<InnsendelserISakDTO> =
        mapNotNull { (innsendingInfo, dokumenter) ->
            val k9FormatInnsending = innsendingInfo.mapTilK9Format()
            if (k9FormatInnsending == null) {
                logger.info("Ignorerer innsending(${innsendingInfo.type}) med journalpostId=${innsendingInfo.journalpostId} fordi den ikke finnes.")
                return@mapNotNull null
            }

            if (k9FormatInnsending.skalIgnorereInnsendelse(innsendingInfo, søkersDokmentoversikt)) {
                return@mapNotNull null
            }

            val søknadId = k9FormatInnsending.søknadId.id
            val legacySøknad = kotlin.runCatching { legacyInnsynApiService.hentLegacySøknad(søknadId) }.getOrNull()

            val innsendelsestype = utledSøknadsType(
                k9FormatSøknad = k9FormatInnsending,
                søknadId = søknadId,
                legacySøknad = legacySøknad
            )

            val arbeidsgivere = when (k9FormatInnsending) {
                is Søknad -> utledArbeidsgivere(legacySøknad, k9FormatInnsending)
                is Ettersendelse -> null
                else -> throw error("Ukjent type av innsending")
            }

            InnsendelserISakDTO(
                søknadId = UUID.fromString(søknadId),
                mottattTidspunkt = innsendingInfo.mottattTidspunkt,
                innsendelsestype = innsendelsestype,
                arbeidsgivere = arbeidsgivere,
                k9FormatInnsendelse = k9FormatInnsending,
                dokumenter = dokumenter
            )
        }

    private fun List<DokumentDTO>.inneholder(søknad: InnsendingInfo) = any { it.journalpostId == søknad.journalpostId }

    private fun utledArbeidsgivere(
        legacySøknad: LegacySøknadDTO?,
        k9FormatSøknad: Søknad,
    ): List<Organisasjon> {
        val arbeidsgivereFraK9Format = k9FormatSøknad.getYtelse<PleiepengerSyktBarn>().arbeidstid.arbeidstakerList
            .map { Organisasjon(it.organisasjonsnummer.verdi, it.organisasjonsnavn) }

        val arbeidsgivereFraLegacySøknad = legacySøknad?.arbeidsgivere() ?: emptyList()

        // Kombinerer begge listene med arbeidsgivere basert på organisasjonsnummer, prioriterer den som har navn.
        return (arbeidsgivereFraLegacySøknad + arbeidsgivereFraK9Format)
            .groupBy { it.organisasjonsnummer }
            .mapValues { (_, orgs) ->
                orgs.maxByOrNull { it.navn.isNullOrBlank() } ?: orgs.first() // Velger den med navn
            }
            .values
            .toList()
    }

    private fun utledSøknadsType(
        k9FormatSøknad: Innsending,
        søknadId: String,
        legacySøknad: LegacySøknadDTO?,
    ): Innsendelsestype {
        return when (k9FormatSøknad) {
            is Søknad -> {
                when (val ks = k9FormatSøknad.kildesystem.getOrNull()) {
                    null -> {
                        logger.info("Fant ingen kildesystem for søknad med søknadId $søknadId.")
                        when (legacySøknad?.søknadstype) {
                            LegacySøknadstype.PP_SYKT_BARN -> Innsendelsestype.SØKNAD
                            LegacySøknadstype.PP_ETTERSENDELSE -> Innsendelsestype.ETTERSENDELSE
                            LegacySøknadstype.PP_LIVETS_SLUTTFASE_ETTERSENDELSE -> Innsendelsestype.ETTERSENDELSE
                            LegacySøknadstype.OMS_ETTERSENDELSE -> Innsendelsestype.ETTERSENDELSE
                            LegacySøknadstype.PP_SYKT_BARN_ENDRINGSMELDING -> Innsendelsestype.ENDRINGSMELDING
                            null -> Innsendelsestype.UKJENT
                        }
                    }

                    Kildesystem.ENDRINGSDIALOG -> Innsendelsestype.ENDRINGSMELDING
                    Kildesystem.SØKNADSDIALOG -> Innsendelsestype.SØKNAD
                    Kildesystem.PUNSJ -> Innsendelsestype.SØKNAD // TODO: Blir dette riktig?
                    Kildesystem.UTLEDET -> Innsendelsestype.SØKNAD // // TODO: Blir dette riktig?

                    else -> throw error("Ukjent kildesystem $ks")
                }
            }

            is Ettersendelse -> return Innsendelsestype.ETTERSENDELSE
            else -> throw error("Ukjent type av innsending")
        }
    }

    fun hentGenerellSaksbehandlingstid(): SaksbehandlingtidDTO {
        val saksbehandlingstidUker = Konstant.FORVENTET_SAKSBEHANDLINGSTID.days.div(7L)
        return SaksbehandlingtidDTO(saksbehandlingstidUker = saksbehandlingstidUker)
    }

    private fun MutableSet<InnsendingInfo>.medTilhørendeDokumenter(søkersDokmentoversikt: List<DokumentDTO>): Map<InnsendingInfo, List<DokumentDTO>> =
        associateWith { søknadInfo: InnsendingInfo ->
            val dokumenterTilknyttetSøknad =
                søkersDokmentoversikt.filter { dokument -> dokument.journalpostId == søknadInfo.journalpostId }
            logger.info("Fant ${dokumenterTilknyttetSøknad.size} dokumenter knyttet til søknaden med journalpostId ${søknadInfo.journalpostId}.")
            dokumenterTilknyttetSøknad
        }

    private fun List<PleietrengendeDTO>.assosierPleietrengendeMedBehandlinger(
        behandlingSupplier: Supplier<Stream<BehandlingDAO>>,
    ): Map<PleietrengendeDTO, MutableList<Behandling>> =
        associateWith { pleietrengendeDTO ->
            val pleietrengendesBehandlinger = behandlingSupplier
                .get()
                .filter { it.pleietrengendeAktørId == pleietrengendeDTO.aktørId }
                .somBehandling()
                .toList()
            logger.info("Fant ${pleietrengendesBehandlinger.size} behandlinger for pleietrengende.")
            pleietrengendesBehandlinger
        }

    private fun InnsendingInfo.mapTilK9Format(): Innsending? {
        return when (type) {
            null, InnsendingType.SØKNAD -> innsendingService.hentSøknad(journalpostId)
                ?.let { JsonUtils.fromString(it.søknad, Søknad::class.java) }

            InnsendingType.ETTERSENDELSE -> innsendingService.hentEttersendelse(journalpostId)
                ?.let { JsonUtils.fromString(it.ettersendelse, Ettersendelse::class.java) }
        }
    }

    private fun BarnOppslagDTO.somPleietrengendeDTO(pleietrengendeSøkerHarOmsorgFor: List<String>): PleietrengendeDTO {
        val søkerHarOmsorgenForPleietrengende = pleietrengendeSøkerHarOmsorgFor.contains(aktørId)
        var fornavn1: String? = this.fornavn
        var mellomnavn1: String? = this.mellomnavn
        var etternavn1: String? = this.etternavn

        if (!søkerHarOmsorgenForPleietrengende) {
            fornavn1 = null
            mellomnavn1 = null
            etternavn1 = null
        }

        return PleietrengendeDTO(
            identitetsnummer = this.identitetsnummer!!,
            fødselsdato = this.fødselsdato,
            fornavn = fornavn1,
            mellomnavn = mellomnavn1,
            etternavn = etternavn1,
            aktørId = this.aktørId
        )
    }

    private fun Set<Aksjonspunkt>.somAksjonspunktDTO(): List<AksjonspunktDTO> =
        map { AksjonspunktDTO(venteårsak = it.venteårsak, tidsfrist = it.tidsfrist) }

    private fun Stream<BehandlingDAO>.somBehandling(): Stream<Behandling> =
        map { JsonUtils.fromString(it.behandling, Behandling::class.java) }
}
