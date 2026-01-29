package no.nav.sifinnsynapi

import no.nav.sifinnsynapi.config.kafka.Topics
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.util.concurrent.TimeUnit
import kotlin.use

@SpringBootApplication(
    exclude = [
        ErrorMvcAutoConfiguration::class
    ]
)
@EnableRetry
@EnableKafka
@EnableTransactionManagement
@EnableScheduling
@ConfigurationPropertiesScan("no.nav.sifinnsynapi")
@EnableConfigurationProperties
class SifInnsynApiApplication

fun main(args: Array<String>) {
    runApplication<SifInnsynApiApplication>(*args) {
        val activeProfiles = System.getenv("SPRING_PROFILES_ACTIVE") ?: System.getProperty("spring.profiles.active") ?: ""
        if (activeProfiles.contains("vtp")) {
            addInitializers(KafkaTopicInitializer())
        }
    }
}


class KafkaTopicInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val env = applicationContext.environment

        val adminClientConfig = mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to env.getProperty("spring.kafka.bootstrap-servers", "kafka:9093"),
            AdminClientConfig.SECURITY_PROTOCOL_CONFIG to env.getProperty("kafka.aiven.properties.security.protocol", "SSL"),
            "ssl.truststore.location" to env.getProperty("kafka.aiven.properties.ssl.trust-store-location")?.removePrefix("file:"),
            "ssl.truststore.password" to env.getProperty("kafka.aiven.properties.ssl.trust-store-password"),
            "ssl.keystore.location" to env.getProperty("kafka.aiven.properties.ssl.key-store-location")?.removePrefix("file:"),
            "ssl.keystore.password" to env.getProperty("kafka.aiven.properties.ssl.key-store-password"),
            "ssl.keystore.type" to env.getProperty("kafka.aiven.properties.ssl.key-store-type")
        )

        AdminClient.create(adminClientConfig).use { adminClient ->
            val topics = listOf(NewTopic(Topics.K9_SAK_TOPIC, 1, 1.toShort()))
            try {
                adminClient.createTopics(topics).all().get(30, TimeUnit.SECONDS)
                println("Created ${topics.size} Kafka topics")
            } catch (e: Exception) {
                println("Topics may already exist: ${e.message}")
            }
        }
    }
}

