package no.nav.sifinnsynapi.utils

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.k9.innsyn.InnsynHendelse
import no.nav.k9.innsyn.PsbSøknadsinnhold
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.KafkaTestUtils
import java.util.*

fun EmbeddedKafkaBroker.opprettK9SakKafkaProducer(): Producer<String, String> {
    val producerProps = KafkaTestUtils.producerProps(this)
    producerProps[ProducerConfig.CLIENT_ID_CONFIG] = "k9-sak-producer-${UUID.randomUUID()}"
    producerProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = "org.apache.kafka.common.serialization.StringSerializer"
    producerProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = "org.apache.kafka.common.serialization.StringSerializer"
    return DefaultKafkaProducerFactory<String, String>(producerProps)
        .createProducer()
}

fun Producer<String, String>.leggPåTopic(hendelse: InnsynHendelse<*>, topic: String): RecordMetadata {
    val logger = LoggerFactory.getLogger("K9SakProducer")
    logger.info("Legger innsynshendelse på topic: {}", hendelse)
    val recordMetadata = send(ProducerRecord(topic, hendelse.somJson())).get()
    logger.info("Innsynshendelse lagt på topic: {}.{} med offset {}", recordMetadata.topic(), recordMetadata.partition(), recordMetadata.offset())
    return recordMetadata
}
