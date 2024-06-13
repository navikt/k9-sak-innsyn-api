package no.nav.sifinnsynapi.konsument.k9sak

import no.nav.k9.innsyn.InnsynHendelse
import no.nav.k9.innsyn.Omsorg
import no.nav.k9.innsyn.PsbSøknadsinnhold
import no.nav.k9.innsyn.SøknadTrukket
import no.nav.k9.innsyn.sak.Behandling
import no.nav.k9.søknad.JsonUtils
import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import no.nav.sifinnsynapi.omsorg.OmsorgDAO
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.sak.behandling.BehandlingDAO
import no.nav.sifinnsynapi.sak.behandling.BehandlingService
import no.nav.sifinnsynapi.soknad.EttersendelseDAO
import no.nav.sifinnsynapi.soknad.PsbSøknadDAO
import no.nav.sifinnsynapi.soknad.InnsendingService
import no.nav.sifinnsynapi.util.Constants
import no.nav.sifinnsynapi.util.MDCUtil
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.*

@Service
class K9SakHendelseKonsument(
    private val innsendingService: InnsendingService,
    private val behandlingService: BehandlingService,
    private val omsorgService: OmsorgService,
    @Value("\${topic.listener.k9-sak.dry-run}") private val dryRun: Boolean,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(K9SakHendelseKonsument::class.java)
    }

    @Transactional(TRANSACTION_MANAGER)
    @KafkaListener(
        topics = ["#{'\${topic.listener.k9-sak.navn}'}"],
        id = "#{'\${topic.listener.k9-sak.id}'}",
        groupId = "#{'\${kafka.aiven.consumer.group-id}'}",
        containerFactory = "aivenKafkaJsonListenerContainerFactory",
        autoStartup = "#{'\${topic.listener.k9-sak.bryter}'}"
    )
    fun konsumer(
        @Payload innsynHendelseJson: String,
    ) {
        when (dryRun) {
            true -> {
                try {
                    logger.info("DRY RUN - Mapper om innsynhendelse...")
                    val innsynHendelse =
                        JsonUtils.fromString(innsynHendelseJson, InnsynHendelse::class.java) as InnsynHendelse<*>
                    when (innsynHendelse.data) {
                        is PsbSøknadsinnhold -> {
                            innsynHendelse as InnsynHendelse<PsbSøknadsinnhold>
                            logger.info("DRY RUN - caster hendelse til InnsynHendelse<PsbSøknadsinnhold>")
                        }

                        is Omsorg -> {
                            innsynHendelse as InnsynHendelse<Omsorg>
                            logger.info("DRY RUN - caster hendelse til InnsynHendelse<Omsorg>")
                        }

                        is SøknadTrukket -> {
                            innsynHendelse as InnsynHendelse<SøknadTrukket>
                            logger.info("DRY RUN - caster hendelse til InnsynHendelse<SøknadTrukket>")
                        }

                        is Behandling -> {
                            innsynHendelse as InnsynHendelse<Behandling>
                            logger.info("DRY RUN - caster hendelse til InnsynHendelse<Behandling>")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("DRY RUN - konsumering av innsynshendelse feilet.", e)
                }
            }

            else -> {
                logger.info("Mapper om innsynhendelse...")
                val innsynHendelse =
                    JsonUtils.fromString(innsynHendelseJson, InnsynHendelse::class.java) as InnsynHendelse<*>
                try {
                    when (innsynHendelse.data) {
                        is PsbSøknadsinnhold -> håndterPsbSøknadsInnhold(innsynHendelse as InnsynHendelse<PsbSøknadsinnhold>)
                        is Omsorg -> håndterOmsorg(innsynHendelse as InnsynHendelse<Omsorg>)
                        is SøknadTrukket -> håndterSøknadTrukket(innsynHendelse as InnsynHendelse<SøknadTrukket>)
                        is Behandling -> håndterBehandling(innsynHendelse as InnsynHendelse<Behandling>)
                    }
                } finally {
                    slettMDC()
                }
            }
        }
    }




    private fun håndterBehandling(innsynHendelse: InnsynHendelse<Behandling>) {
        val behandling = innsynHendelse.data
        settOppMdcBehandling(behandling)

        logger.info("Innsynhendelse mappet til Behandling.")
        logger.trace("Lagrer Behandling med behandlingsId: {}...", behandling.behandlingsId)

        val resultat = gyldigBehandling(innsynHendelse)
        if (!resultat.ok) {
            logger.warn("Behandling={} med saksnummer={} opprettet={} fra k9-sak er ugyldig og vil bli ignorert: {} ",
                behandling.behandlingsId, behandling.fagsak.saksnummer, behandling.opprettetTidspunkt, resultat.forklaring)
            return
        }
        behandlingService.lagreBehandling(innsynHendelse.somBehandlingDAO())
        logger.trace("Behandling lagret.")
    }

    private fun gyldigBehandling(innsynHendelse: InnsynHendelse<Behandling>): Validering {
        if (innsynHendelse.data.fagsak.pleietrengendeAktørId == null) {
            return Validering(false, "Pleietrengende er tom")
        }
        return Validering(true)
    }

    private data class Validering(val ok: Boolean, val forklaring: String? = null)

    private fun håndterSøknadTrukket(innsynHendelse: InnsynHendelse<SøknadTrukket>) {
        val data = innsynHendelse.data
        settOppMdcSøknadTrukket(data)

        logger.info("Innsynhendelse mappet til SøknadTrukket.")

        val journalpostId = data.journalpostId
        logger.trace("Trekker tilbake søknad med journalpostId = {} ...", journalpostId)
        if (innsendingService.trekkSøknad(journalpostId)) logger.trace("Søknad er trukket tilbake", journalpostId)
        else throw IllegalStateException("Søknad ble ikke trukket tilbake.")
    }

    private fun håndterPsbSøknadsInnhold(innsynHendelse: InnsynHendelse<PsbSøknadsinnhold>) {
        val data = innsynHendelse.data
        settOppMdcSøknad(data)

        logger.info("Innsynhendelse mappet til PsbSøknadsinnhold.")

        logger.trace("Lagrer PsbSøknadsinnhold med journalpostId: {}...", data.journalpostId)

        if (data.søknad != null && data.ettersendelse != null) {
            logger.warn("Både søknad og ettersendelse er satt! Forventet kun ene")
            throw IllegalStateException("Både søknad og ettersendelse er satt! Forventet kun en.")
        }

        if (data.søknad != null) {
            logger.info("Lagerer søknad")
            innsendingService.lagreSøknad(innsynHendelse.somPsbSøknadDAO())
        }
        if (data.ettersendelse != null) {
            logger.info("Lagrer ettersendelse")
            innsendingService.lagreEttersendelse(innsynHendelse.somEttersendelseDAO())
        }

        logger.trace("PsbSøknadsinnhold lagret.")
    }

    private fun håndterOmsorg(innsynHendelse: InnsynHendelse<Omsorg>) {
        logger.info("Innsynhendelse mappet til Omsorg.")

        val omsorg = innsynHendelse.data
        when (omsorgService.omsorgEksisterer(omsorg.søkerAktørId, omsorg.pleietrengendeAktørId)) {
            true -> {
                logger.trace("Oppdaterer Omsorg...")
                omsorgService.oppdaterOmsorg(
                    søkerAktørId = omsorg.søkerAktørId,
                    pleietrengendeAktørId = omsorg.pleietrengendeAktørId,
                    harOmsorgen = omsorg.isHarOmsorgen
                )
                logger.trace("Omsorg oppdatert.")
            }

            else -> {
                logger.trace("Lagrer Omsorg...")
                omsorgService.lagreOmsorg(innsynHendelse.somOmsorgDAO())
                logger.trace("Omsorg lagret.")
            }
        }
    }

    private fun settOppMdcBehandling(behandling: Behandling) {
        MDCUtil.toMDC(Constants.BEHANDLING_ID, behandling.behandlingsId)
        MDCUtil.toMDC(Constants.SAKSNUMMER, behandling.fagsak.saksnummer.verdi)
    }

    private fun settOppMdcSøknadTrukket(søknadTrukket: SøknadTrukket) {
        MDCUtil.toMDC(Constants.JOURNALPOST_ID, søknadTrukket.journalpostId)
    }

    private fun settOppMdcSøknad(innsending: PsbSøknadsinnhold) {
        MDCUtil.toMDC(
            Constants.SØKNAD_ID,
            innsending.søknad?.søknadId?.id ?: innsending.ettersendelse?.søknadId?.id
        )
        MDCUtil.toMDC(Constants.JOURNALPOST_ID, innsending.journalpostId)
    }

    private fun slettMDC() {
        MDC.remove(Constants.BEHANDLING_ID)
        MDC.remove(Constants.SAKSNUMMER)
        MDC.remove(Constants.JOURNALPOST_ID)
        MDC.remove(Constants.SØKNAD_ID)
    }
}

private fun InnsynHendelse<Behandling>.somBehandlingDAO(): BehandlingDAO {
    return BehandlingDAO(
        behandlingId = data.behandlingsId,
        søkerAktørId = data.fagsak.søkerAktørId.id,
        pleietrengendeAktørId = data.fagsak.pleietrengendeAktørId.id,
        saksnummer = data.fagsak.saksnummer.verdi,
        ytelsetype = data.fagsak.ytelseType,
        behandling = JsonUtils.toString(data),
        opprettetDato = ZonedDateTime.now(UTC),
        oppdatertDato = oppdateringstidspunkt
    )
}

private fun InnsynHendelse<PsbSøknadsinnhold>.somPsbSøknadDAO() = PsbSøknadDAO(
    journalpostId = data.journalpostId,
    søkerAktørId = data.søkerAktørId,
    pleietrengendeAktørId = data.pleietrengendeAktørId,
    søknad = JsonUtils.toString(data.søknad),
    opprettetDato = ZonedDateTime.now(UTC),
    oppdatertDato = oppdateringstidspunkt
)


private fun InnsynHendelse<PsbSøknadsinnhold>.somEttersendelseDAO() = EttersendelseDAO(
    journalpostId = data.journalpostId,
    søkerAktørId = data.søkerAktørId,
    pleietrengendeAktørId = data.pleietrengendeAktørId,
    ettersendelse = JsonUtils.toString(data.ettersendelse),
    opprettetDato = ZonedDateTime.now(UTC),
    oppdatertDato = oppdateringstidspunkt
)

private fun InnsynHendelse<Omsorg>.somOmsorgDAO() = OmsorgDAO(
    id = UUID.randomUUID().toString(),
    søkerAktørId = data.søkerAktørId,
    pleietrengendeAktørId = data.pleietrengendeAktørId,
    harOmsorgen = data.isHarOmsorgen,
    opprettetDato = ZonedDateTime.now(UTC),
    oppdatertDato = oppdateringstidspunkt
)
