package no.nav.sifinnsynapi.config.kafka

import no.nav.sifinnsynapi.config.kafka.CommonKafkaConfig.Companion.configureConcurrentKafkaListenerContainerFactory
import no.nav.sifinnsynapi.config.kafka.CommonKafkaConfig.Companion.consumerFactory
import no.nav.sifinnsynapi.config.kafka.CommonKafkaConfig.Companion.kafkaTemplate
import no.nav.sifinnsynapi.config.kafka.CommonKafkaConfig.Companion.kafkaTransactionManager
import no.nav.sifinnsynapi.config.kafka.CommonKafkaConfig.Companion.producerFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.transaction.KafkaTransactionManager

@Configuration
class AivenKafkaConfig(
    private val kafkaClusterProperties: KafkaClusterProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(AivenKafkaConfig::class.java)
    }

    @Bean
    fun aivenConsumerFactory(): ConsumerFactory<String, String> = consumerFactory(kafkaClusterProperties.aiven)

    @Bean
    fun aivenProducerFactory(): ProducerFactory<String, String> = producerFactory(kafkaClusterProperties.aiven)

    @Bean
    fun aivenKafkaTemplate(aivenProducerFactory: ProducerFactory<String, String>) =
        kafkaTemplate(aivenProducerFactory, kafkaClusterProperties.aiven)

    @Bean
    fun aivenKafkaTransactionManager(aivenProducerFactory: ProducerFactory<String, String>) =
        kafkaTransactionManager(aivenProducerFactory, kafkaClusterProperties.aiven)

    @Bean
    fun aivenKafkaJsonListenerContainerFactory(
        aivenConsumerFactory: ConsumerFactory<String, String>,
        aivenKafkaTemplate: KafkaTemplate<String, String>,
        aivenKafkaTransactionManager: KafkaTransactionManager<String, String>,
        environment: Environment
    ): ConcurrentKafkaListenerContainerFactory<String, String> = configureConcurrentKafkaListenerContainerFactory(
        clientId = kafkaClusterProperties.aiven.consumer.groupId,
        consumerFactory = aivenConsumerFactory,
        retryInterval = kafkaClusterProperties.aiven.consumer.retryInterval,
        transactionManager = aivenKafkaTransactionManager,
        kafkaTemplate = aivenKafkaTemplate,
        logger = logger,
        activeProfiles = environment.activeProfiles
    )
}
