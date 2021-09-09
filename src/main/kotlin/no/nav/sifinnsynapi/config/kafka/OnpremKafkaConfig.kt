package no.nav.sifinnsynapi.config.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.config.kafka.CommonKafkaConfig.Companion.configureConcurrentKafkaListenerContainerFactory
import no.nav.sifinnsynapi.soknad.SøknadRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.transaction.KafkaTransactionManager


@Configuration
class OnpremKafkaConfig(
    private val objectMapper: ObjectMapper,
    private val søknadRepository: SøknadRepository,
    private val kafkaClusterProperties: KafkaClusterProperties,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OnpremKafkaConfig::class.java)
    }

    @Bean
    fun onpremConsumerFactory(): ConsumerFactory<String, String> =
        CommonKafkaConfig.consumerFactory(kafkaClusterProperties.onprem)

    @Bean
    fun onpremProducerFactory(): ProducerFactory<String, String> =
        CommonKafkaConfig.producerFactory(kafkaClusterProperties.onprem)

    @Bean
    fun onpremKafkaTemplate(onpremProducerFactory: ProducerFactory<String, String>) =
        CommonKafkaConfig.kafkaTemplate(onpremProducerFactory, kafkaClusterProperties.onprem)

    @Bean
    fun onpremKafkaTransactionManager(onpremProducerFactory: ProducerFactory<String, String>) =
        CommonKafkaConfig.kafkaTransactionManager(onpremProducerFactory, kafkaClusterProperties.onprem)

    @Bean
    fun onpremKafkaJsonListenerContainerFactory(
        onpremConsumerFactory: ConsumerFactory<String, String>,
        onpremKafkaTemplate: KafkaTemplate<String, String>,
        onpremKafkaTransactionManager: KafkaTransactionManager<String, String>,
    ): ConcurrentKafkaListenerContainerFactory<String, String> = configureConcurrentKafkaListenerContainerFactory(
        clientId = kafkaClusterProperties.aiven.consumer.groupId,
        consumerFactory = onpremConsumerFactory,
        transactionManager = onpremKafkaTransactionManager,
        kafkaTemplate = onpremKafkaTemplate,
        retryInterval = kafkaClusterProperties.aiven.consumer.retryInterval,
        objectMapper = objectMapper,
        søknadRepository = søknadRepository,
        logger = logger
    )
}
