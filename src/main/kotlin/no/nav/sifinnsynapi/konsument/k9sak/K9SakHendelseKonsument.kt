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
import no.nav.sifinnsynapi.soknad.SøknadRepository
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
    private val repository: SøknadRepository,
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
            is PsbSøknadsinnhold ->  håndterPsbSøknadsInnhold(innsynHendelse as InnsynHendelse<PsbSøknadsinnhold>)
            is Omsorg -> håndterOmsorg(innsynHendelse as InnsynHendelse<Omsorg>)
            is SøknadTrukket -> TODO("Ikke implemenert enda.")
            else -> {
                throw IllegalStateException("Ikke støttet data type på InnsynHendelse.")
            }
        }
    }

    private fun håndterPsbSøknadsInnhold(innsynHendelse: InnsynHendelse<PsbSøknadsinnhold>) {
        logger.info("Innsynhendelse mappet til PsbSøknadsinnhold.")

        logger.info("Lagrer PsbSøknadsinnhold med journalpostId: {}...", innsynHendelse.data.journalpostId)
        repository.save(innsynHendelse.somPsbSøknadDAO())
        logger.info("PsbSøknadsinnhold lagret.")
    }

    private fun håndterOmsorg(innsynHendelse: InnsynHendelse<Omsorg>) {
        logger.info("Innsynhendelse mappet til Omsorg.")

        val omsorg = innsynHendelse.data
        when (omsorgService.omsorgEksisterer(omsorg.søkerAktørId, omsorg.pleietrengendeAktørId)) {
            true -> {
                logger.info("Oppdaterer Omsorg...")
                logger.info("Omsorg oppdatert: {}.", omsorgService.oppdaterOmsorg(
                    søkerAktørId = omsorg.søkerAktørId,
                    pleietrengendeAktørId = omsorg.pleietrengendeAktørId,
                    harOmsorgen = omsorg.isHarOmsorgen
                ))
            }
            else -> {
                logger.info("Lagrer Omsorg...")
                logger.info("Omsorg lagret: {}.", omsorgService.lagre(innsynHendelse.somOmsorgDAO()))
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
