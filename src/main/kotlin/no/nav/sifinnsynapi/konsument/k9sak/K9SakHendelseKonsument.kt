package no.nav.sifinnsynapi.konsument.k9sak

import no.nav.k9.innsyn.InnsynHendelse
import no.nav.k9.innsyn.PsbSøknadsinnhold
import no.nav.k9.søknad.JsonUtils
import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import no.nav.sifinnsynapi.oppslag.OppslagsService
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

@Service
class K9SakHendelseKonsument(
    private val repository: SøknadRepository,
    private val oppslagsService: OppslagsService,
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
        val innsynPsbSøknadHendelse: InnsynHendelse<PsbSøknadsinnhold> = when (innsynHendelse) {
            is PsbSøknadsinnhold -> innsynHendelse as InnsynHendelse<PsbSøknadsinnhold>
            else -> throw IllegalStateException("Ukjent data type på InnsynHendelse.")
        }
        logger.info("Innsynhendelse mappet.")

        logger.info("Lagrer innsynhendelse...")
        repository.save(innsynPsbSøknadHendelse.somPsbSøknadDAO())
        logger.info("Innsynhendelse lagret.")
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
