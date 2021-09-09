package no.nav.sifinnsynapi.konsument.k9sak

import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.common.PersonIdentifikator
import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.soknad.SøknadDAO
import no.nav.sifinnsynapi.soknad.SøknadRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.*

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
        @Payload søknad: Søknad
    ) {
        logger.info("Mottatt hendelse fra k9-sak: {}", Søknad.SerDes.serialize(søknad))

        val søknadDAO = SøknadDAO(
            søknadId = UUID.fromString(søknad.søknadId.id),
            personIdent = PersonIdentifikator(søknad.søker.personIdent.verdi),
            søknad = søknad,
            opprettet = ZonedDateTime.now()
        )

        repository.save(søknadDAO)
    }
}
