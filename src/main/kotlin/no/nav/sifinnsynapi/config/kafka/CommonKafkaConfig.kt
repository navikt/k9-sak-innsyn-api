package no.nav.sifinnsynapi.config.kafka

import no.nav.k9.innsyn.InnsynHendelse
import no.nav.k9.innsyn.Omsorg
import no.nav.k9.innsyn.PsbSøknadsinnhold
import no.nav.k9.innsyn.SøknadTrukket
import no.nav.k9.innsyn.sak.Behandling
import no.nav.k9.søknad.JsonUtils
import no.nav.sifinnsynapi.util.Constants
import no.nav.sifinnsynapi.util.MDCUtil
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.header.Header
import org.slf4j.Logger
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultAfterRollbackProcessor
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.kafka.transaction.KafkaTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.util.backoff.FixedBackOff
import java.nio.ByteBuffer
import java.time.Duration

class CommonKafkaConfig {
    companion object {
        fun commonConfig(kafkaConfigProps: KafkaConfigProperties) = mutableMapOf<String, Any>().apply {
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaConfigProps.servers)
        } + securityConfig(kafkaConfigProps.properties)

        fun securityConfig(securityProps: KafkaProperties?) = mutableMapOf<String, Any>().apply {
            put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "") // Disable server host name verification
            securityProps?.let { props: KafkaProperties ->
                val sslProps = props.ssl

                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, props.security.protocol)
                put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, sslProps.trustStoreLocation.file.absolutePath)
                put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, sslProps.trustStorePassword)
                put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, sslProps.trustStoreType)

                props.sasl?.let { saslProps: KafkaSaslProperties ->
                    put(SaslConfigs.SASL_MECHANISM, saslProps.mechanism)
                    put(SaslConfigs.SASL_JAAS_CONFIG, saslProps.jaasConfig)
                }

                sslProps.keyStoreLocation?.let { put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, it.file.absolutePath) }
                sslProps.keyStorePassword?.let { put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, it) }
                sslProps.keyStoreType?.let { put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, it) }
            }
        }

        fun consumerFactory(kafkaConfigProps: KafkaConfigProperties): ConsumerFactory<String, String> {
            val consumerProps = kafkaConfigProps.consumer
            return DefaultKafkaConsumerFactory(
                mutableMapOf<String, Any>(
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to consumerProps.enableAutoCommit,
                    ConsumerConfig.GROUP_ID_CONFIG to consumerProps.groupId,
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to consumerProps.autoOffsetReset,
                    ConsumerConfig.ISOLATION_LEVEL_CONFIG to consumerProps.isolationLevel,
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to consumerProps.keyDeserializer,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to consumerProps.valueDeserializer
                ) + commonConfig(kafkaConfigProps)
            )
        }

        fun producerFactory(kafkaConfigProps: KafkaConfigProperties): ProducerFactory<String, String> {
            val producerProps = kafkaConfigProps.producer
            val factory = DefaultKafkaProducerFactory<String, String>(
                mutableMapOf<String, Any>(
                    ProducerConfig.CLIENT_ID_CONFIG to producerProps.clientId,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to producerProps.keySerializer,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to producerProps.valueSerializer,
                    ProducerConfig.RETRIES_CONFIG to producerProps.retries,
                ) + commonConfig(kafkaConfigProps)
            )

            factory.setTransactionIdPrefix(producerProps.transactionIdPrefix)
            return factory
        }

        fun kafkaTemplate(producerFactory: ProducerFactory<String, String>, kafkaConfigProps: KafkaConfigProperties) =
            KafkaTemplate(producerFactory).apply {
                setTransactionIdPrefix(kafkaConfigProps.producer.transactionIdPrefix)
            }

        fun kafkaTransactionManager(
            producerFactory: ProducerFactory<String, String>,
            kafkaConfigProps: KafkaConfigProperties
        ) =
            KafkaTransactionManager(producerFactory).apply {
                setTransactionIdPrefix(kafkaConfigProps.producer.transactionIdPrefix)
            }

        fun configureConcurrentKafkaListenerContainerFactory(
            clientId: String,
            consumerFactory: ConsumerFactory<String, String>,
            retryInterval: Long,
            transactionManager: PlatformTransactionManager,
            kafkaTemplate: KafkaTemplate<String, String>,
            logger: Logger,
            activeProfiles: Array<String>
        ): ConcurrentKafkaListenerContainerFactory<String, String> {
            val factory = ConcurrentKafkaListenerContainerFactory<String, String>()

            factory.consumerFactory = consumerFactory

            factory.setReplyTemplate(kafkaTemplate)

            factory.setRecordInterceptor { record: ConsumerRecord<String, String>, consumer: Consumer<String, String> ->
                MDCUtil.toMDC(Constants.NAV_CONSUMER_ID, clientId)
                logger.loggAntallForsøk(record)
                record
            }

            // VTP kafka broker støtter ikke transaksjoner
            if (!activeProfiles.contains("vtp")) {
            // https://docs.spring.io/spring-kafka/docs/2.5.2.RELEASE/reference/html/#chained-transaction-manager
                factory.containerProperties.transactionManager = transactionManager
            }


            // https://docs.spring.io/spring-kafka/docs/2.5.2.RELEASE/reference/html/#exactly-once
            factory.containerProperties.eosMode = ContainerProperties.EOSMode.V2

            // https://docs.spring.io/spring-kafka/docs/2.5.2.RELEASE/reference/html/#committing-offsets
            factory.containerProperties.ackMode = ContainerProperties.AckMode.RECORD;

            // https://docs.spring.io/spring-kafka/docs/2.5.2.RELEASE/reference/html/#delivery-header
            factory.containerProperties.isDeliveryAttemptHeader = true

            // https://docs.spring.io/spring-kafka/reference/html/#listener-container
            factory.containerProperties.setAuthExceptionRetryInterval(Duration.ofSeconds(10L))

            //https://docs.spring.io/spring-kafka/docs/2.5.2.RELEASE/reference/html/#after-rollback

            factory.setAfterRollbackProcessor(defaultAfterRollbackProsessor(logger, retryInterval))
            return factory
        }

        private fun Logger.loggAntallForsøk(
            it: ConsumerRecord<String, String>
        ) {
            val lastHeader: Header? = it.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT)
            val antallForsøk = lastHeader?.let { ByteBuffer.wrap(it.value()).int } ?: 0

            if (antallForsøk > 1) warn("Konsumering av ${it.topic()}-${it.partition()} med offset ${it.offset()} feilet første gang. Prøver for $antallForsøk gang.")
        }

        private fun defaultAfterRollbackProsessor(logger: Logger, retryInterval: Long) =
            DefaultAfterRollbackProcessor<String, String>(
                defaultRecoverer(logger), FixedBackOff(retryInterval, Long.MAX_VALUE)
            ).apply {
                setClassifications(mapOf(), true)
            }


        fun defaultRecoverer(logger: Logger) = ConsumerRecordRecoverer { cr: ConsumerRecord<*, *>, ex: Exception ->
            logger.error("Retry attempts exhausted for ${cr.topic()}-${cr.partition()}@${cr.offset()}", ex)
        }
    }
}
