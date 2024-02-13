package no.nav.sifinnsynapi.konsument.k9sak

import no.nav.k9.innsyn.*
import no.nav.k9.innsyn.sak.Behandling
import no.nav.k9.søknad.JsonUtils
import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import no.nav.sifinnsynapi.omsorg.OmsorgDAO
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.sak.behandling.BehandlingDAO
import no.nav.sifinnsynapi.sak.behandling.BehandlingService
import no.nav.sifinnsynapi.soknad.PsbSøknadDAO
import no.nav.sifinnsynapi.soknad.SøknadService
import org.slf4j.LoggerFactory
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
    private val søknadService: SøknadService,
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

                when (innsynHendelse.data) {
                    is PsbSøknadsinnhold -> håndterPsbSøknadsInnhold(innsynHendelse as InnsynHendelse<PsbSøknadsinnhold>)
                    is Omsorg -> håndterOmsorg(innsynHendelse as InnsynHendelse<Omsorg>)
                    is SøknadTrukket -> håndterSøknadTrukket(innsynHendelse as InnsynHendelse<SøknadTrukket>)
                    is Behandling -> håndterBehandling(innsynHendelse as InnsynHendelse<Behandling>)
                }
            }
        }
    }

    private fun håndterBehandling(innsynHendelse: InnsynHendelse<Behandling>) {
        logger.info("Innsynhendelse mappet til Behandling.")

        val behandling = innsynHendelse.data
        logger.trace("Lagrer Behandling med behandlingsId: {}...", behandling.behandlingsId)
        behandlingService.lagreBehandling(innsynHendelse.somBehandlingDAO())
        logger.trace("Behandling lagret.")
    }

    private fun håndterSøknadTrukket(innsynHendelse: InnsynHendelse<SøknadTrukket>) {
        logger.info("Innsynhendelse mappet til SøknadTrukket.")

        val journalpostId = innsynHendelse.data.journalpostId
        logger.trace("Trekker tilbake søknad med journalpostId = {} ...", journalpostId)
        if (søknadService.trekkSøknad(journalpostId)) logger.trace("Søknad er trukket tilbake", journalpostId)
        else throw IllegalStateException("Søknad ble ikke trukket tilbake.")
    }

    private fun håndterPsbSøknadsInnhold(innsynHendelse: InnsynHendelse<PsbSøknadsinnhold>) {
        logger.info("Innsynhendelse mappet til PsbSøknadsinnhold.")

        logger.trace("Lagrer PsbSøknadsinnhold med journalpostId: {}...", innsynHendelse.data.journalpostId)
        søknadService.lagreSøknad(innsynHendelse.somPsbSøknadDAO())
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
}

private fun InnsynHendelse<Behandling>.somBehandlingDAO(): BehandlingDAO {
    return BehandlingDAO(
        behandlingId = data.behandlingsId,
        søkerAktørId = data.fagsak.søkerAktørId.id,
        pleietrengendeAktørId = data.fagsak.pleietrengendeAktørId.id,
        saksnummer = data.fagsak.saksnummer.verdi,
        ytelsetype = data.fagsak.ytelseType,
        behandling = JsonUtils.toString(data, TempObjectMapperKodeverdi.getObjectmapper()),
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

private fun InnsynHendelse<Omsorg>.somOmsorgDAO() = OmsorgDAO(
    id = UUID.randomUUID().toString(),
    søkerAktørId = data.søkerAktørId,
    pleietrengendeAktørId = data.pleietrengendeAktørId,
    harOmsorgen = data.isHarOmsorgen,
    opprettetDato = ZonedDateTime.now(UTC),
    oppdatertDato = oppdateringstidspunkt
)
