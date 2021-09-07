package no.nav.sifinnsynapi.konsument.k9sak

import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.common.AktørId
import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import no.nav.sifinnsynapi.soknad.SøknadDAO
import no.nav.sifinnsynapi.soknad.SøknadRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class K9SakHendelseKonsument(
    private val repository: SøknadRepository,
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
        @Payload hendelse: Søknad
    ) {
        logger.info("Mottatt hendelse fra k9-sak: {}", hendelse)

        val søknadDAO = SøknadDAO(
            søknadId = UUID.fromString(hendelse.søknadId.id),
            aktørId = AktørId("123456"),
            søknad = hendelse
        )

        repository.save(søknadDAO)
    }
}
