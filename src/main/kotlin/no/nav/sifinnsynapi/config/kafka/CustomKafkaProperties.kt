package no.nav.sifinnsynapi.config.kafka

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.core.io.Resource
import org.springframework.validation.annotation.Validated

@ConstructorBinding
@ConfigurationProperties(prefix = "kafka")
@Validated
data class KafkaClusterProperties(
    val onprem: KafkaConfigProperties,
    val aiven: KafkaConfigProperties,
)

data class KafkaConfigProperties(
    val servers: String,
    val consumer: KafkaConsumerProperties,
    val producer: KafkaProducerProperties,
    val properties: KafkaProperties? = null
)

data class KafkaConsumerProperties(
    val enableAutoCommit: Boolean,
    val groupId: String,
    val autoOffsetReset: String,
    val isolationLevel: String,
    val retryInterval: Long,
    val keyDeserializer: String,
    val valueDeserializer: String,
    val schemaRegistryUrl: String
)

data class KafkaProducerProperties(
    val clientId: String,
    val keySerializer: String,
    val valueSerializer: String,
    val retries: Int,
    val transactionIdPrefix: String
)

data class KafkaProperties(
    val security: KafkaSecurityProperties,
    val sasl: KafkaSaslProperties? = null,
    val ssl: KafkaSslProperties
)

data class KafkaSecurityProperties(
    val protocol: String
)

data class KafkaSaslProperties(
    val mechanism: String,
    val jaasConfig: String,
)

data class KafkaSslProperties(
    val trustStoreLocation: Resource,
    val trustStorePassword: String,
    val trustStoreType: String,
    val keyStoreLocation: Resource? = null,
    val keyStorePassword: String? = null,
    val keyStoreType: String? = null,
)
