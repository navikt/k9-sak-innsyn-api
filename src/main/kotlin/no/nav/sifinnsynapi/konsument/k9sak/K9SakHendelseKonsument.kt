package no.nav.sifinnsynapi.konsument.k9sak

import no.nav.k9.innsyn.InnsynHendelse
import no.nav.k9.innsyn.Omsorg
import no.nav.k9.innsyn.PsbSøknadsinnhold
import no.nav.k9.innsyn.SøknadTrukket
import no.nav.k9.søknad.JsonUtils
import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import no.nav.sifinnsynapi.omsorg.OmsorgDAO
import no.nav.sifinnsynapi.omsorg.OmsorgService
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
    private val omsorgService: OmsorgService,
    @Value("\${topic.listener.k9-sak.dry-run}") private val dryRun: Boolean
) {

    companion object {
        private val logger = LoggerFactory.getLogger(K9SakHendelseKonsument::class.java)
    }

    @Transactional(TRANSACTION_MANAGER)
    @KafkaListener(
        topics = ["#{'\${topic.listener.k9-sak.navn}'}"],
        id = "#{'\${topic.listener.k9-sak.id}'}",
        groupId = "#{'\${kafka.onprem.consumer.group-id}'}",
        containerFactory = "aivenKafkaJsonListenerContainerFactory",
        autoStartup = "#{'\${topic.listener.k9-sak.bryter}'}"
    )
    fun konsumer(
        @Payload innsynHendelseJson: String
    ) {
        logger.info("Mapper om innsynhendelse...")
        val innsynHendelse = JsonUtils.fromString(innsynHendelseJson, InnsynHendelse::class.java) as InnsynHendelse<*>

        when (innsynHendelse.data) {
            is PsbSøknadsinnhold -> håndterPsbSøknadsInnhold(innsynHendelse as InnsynHendelse<PsbSøknadsinnhold>)
            is Omsorg -> håndterOmsorg(innsynHendelse as InnsynHendelse<Omsorg>)
            is SøknadTrukket -> håndterSøknadTrukket(innsynHendelse as InnsynHendelse<SøknadTrukket>)
            else -> {
                throw IllegalStateException("Ikke støttet data type på InnsynHendelse.")
            }
        }
    }

    private fun håndterSøknadTrukket(innsynHendelse: InnsynHendelse<SøknadTrukket>) {
        logger.trace("Innsynhendelse mappet til SøknadTrukket.")

        val journalpostId = innsynHendelse.data.journalpostId
        logger.info("Trekker tilbake søknad med journalpostId = {} ...", journalpostId)
        if (søknadService.trekkSøknad(journalpostId)) logger.info("Søknad er trukket tilbake", journalpostId)
         else throw IllegalStateException("Søknad ble ikke trukket tilbake.")
    }

    private fun håndterPsbSøknadsInnhold(innsynHendelse: InnsynHendelse<PsbSøknadsinnhold>) {
        logger.trace("Innsynhendelse mappet til PsbSøknadsinnhold.")

        logger.info("Lagrer PsbSøknadsinnhold med journalpostId: {}...", innsynHendelse.data.journalpostId)
        søknadService.lagreSøknad(innsynHendelse.somPsbSøknadDAO())
        logger.info("PsbSøknadsinnhold lagret.")
    }

    private fun håndterOmsorg(innsynHendelse: InnsynHendelse<Omsorg>) {
        logger.trace("Innsynhendelse mappet til Omsorg.")

        val omsorg = innsynHendelse.data
        when (omsorgService.omsorgEksisterer(omsorg.søkerAktørId, omsorg.pleietrengendeAktørId)) {
            true -> {
                logger.info("Oppdaterer Omsorg...")
                omsorgService.oppdaterOmsorg(
                    søkerAktørId = omsorg.søkerAktørId,
                    pleietrengendeAktørId = omsorg.pleietrengendeAktørId,
                    harOmsorgen = omsorg.isHarOmsorgen
                )
                logger.info("Omsorg oppdatert.")
            }
            else -> {
                logger.info("Lagrer Omsorg...")
                omsorgService.lagre(innsynHendelse.somOmsorgDAO())
                logger.info("Omsorg lagret.")
            }
        }
    }
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
