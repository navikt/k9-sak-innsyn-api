package no.nav.sifinnsynapi

import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication(exclude = [
    ErrorMvcAutoConfiguration::class
])
@EnableRetry
@EnableKafka
@EnableTransactionManagement
@EnableScheduling
@ConfigurationPropertiesScan("no.nav.sifinnsynapi")
@EnableConfigurationProperties
class SifInnsynApiApplication

fun main(args: Array<String>) {
    runApplication<SifInnsynApiApplication>(*args)
}

// TODO: Fjern etter reparasjon av flyway
@Configuration
class FlywayRepairConfig(private val flyway: Flyway) {
    init {
        flyway.repair()
    }
}
