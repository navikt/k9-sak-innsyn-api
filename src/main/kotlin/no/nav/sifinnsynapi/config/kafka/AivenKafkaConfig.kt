package no.nav.sifinnsynapi.config.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.config.kafka.CommonKafkaConfig.Companion.configureConcurrentKafkaListenerContainerFactory
import no.nav.sifinnsynapi.config.kafka.CommonKafkaConfig.Companion.consumerFactory
import no.nav.sifinnsynapi.config.kafka.CommonKafkaConfig.Companion.kafkaTemplate
import no.nav.sifinnsynapi.config.kafka.CommonKafkaConfig.Companion.kafkaTransactionManager
import no.nav.sifinnsynapi.config.kafka.CommonKafkaConfig.Companion.producerFactory
import no.nav.sifinnsynapi.soknad.SøknadRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.transaction.KafkaTransactionManager

@Configuration
class AivenKafkaConfig(
    private val objectMapper: ObjectMapper,
    private val søknadRepository: SøknadRepository,
    private val kafkaClusterProperties: KafkaClusterProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(AivenKafkaConfig::class.java)
    }

    @Bean
    fun aivenConsumerFactory(): ConsumerFactory<String, Søknad> = consumerFactory(kafkaClusterProperties.aiven)

    @Bean
    fun aivenProducerFactory(): ProducerFactory<String, Søknad> = producerFactory(kafkaClusterProperties.aiven)

    @Bean
    fun aivenKafkaTemplate(aivenProducerFactory: ProducerFactory<String, Søknad>) =
        kafkaTemplate(aivenProducerFactory, kafkaClusterProperties.aiven)

    @Bean
    fun aivenKafkaTransactionManager(aivenProducerFactory: ProducerFactory<String, Søknad>) =
        kafkaTransactionManager(aivenProducerFactory, kafkaClusterProperties.aiven)

    @Bean
    fun aivenKafkaJsonListenerContainerFactory(
        aivenConsumerFactory: ConsumerFactory<String, Søknad>,
        aivenKafkaTemplate: KafkaTemplate<String, Søknad>,
        aivenKafkaTransactionManager: KafkaTransactionManager<String, Søknad>
    ): ConcurrentKafkaListenerContainerFactory<String, Søknad> = configureConcurrentKafkaListenerContainerFactory(
        clientId = kafkaClusterProperties.aiven.consumer.groupId,
        consumerFactory = aivenConsumerFactory,
        kafkaTemplate = aivenKafkaTemplate,
        transactionManager = aivenKafkaTransactionManager,
        retryInterval = kafkaClusterProperties.aiven.consumer.retryInterval,
        objectMapper = objectMapper,
        søknadRepository = søknadRepository,
        logger = logger
    )
}
