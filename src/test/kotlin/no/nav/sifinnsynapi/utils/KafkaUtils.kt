package no.nav.sifinnsynapi.utils

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.k9.søknad.Søknad
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.KafkaTestUtils
import java.util.*

fun EmbeddedKafkaBroker.opprettK9SakKafkaProducer(): Producer<String, Any> {
    val producerProps = KafkaTestUtils.producerProps(this)
    producerProps[ProducerConfig.CLIENT_ID_CONFIG] = "k9-sak-producer-${UUID.randomUUID()}"
    producerProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = "org.apache.kafka.common.serialization.StringSerializer"
    producerProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = "org.apache.kafka.common.serialization.StringSerializer"
    return DefaultKafkaProducerFactory<String, Any>(producerProps)
        .createProducer()
}

fun Producer<String, Any>.leggPåTopic(hendelse: Søknad, topic: String, mapper: ObjectMapper): RecordMetadata {
    return send(ProducerRecord(topic,  hendelse.somJson(mapper))).get()
}
