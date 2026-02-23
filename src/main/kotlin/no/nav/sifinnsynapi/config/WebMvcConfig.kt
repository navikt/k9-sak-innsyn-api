package no.nav.sifinnsynapi.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.boot.jackson2.autoconfigure.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

@Configuration
class WebMvcConfig() : WebMvcConfigurer {

    companion object {
        val log: Logger = LoggerFactory.getLogger(WebMvcConfigurer::class.java)
    }

    @Bean
    fun jacksonBuilderCustomizer(): Jackson2ObjectMapperBuilderCustomizer {
        log.info("-------> Customizing builder")
        return Jackson2ObjectMapperBuilderCustomizer { builder ->
            builder.featuresToDisable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS
            )
            builder.propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
        }
    }

    @Bean
    fun jackson3BuilderCustomizer(): JsonMapperBuilderCustomizer {
        log.info("-------> Customizing jackson 3 JsonMapper builder")
        return JsonMapperBuilderCustomizer { builder ->
            builder
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Unngå å prefikse kotlin data klasse felter med prefiks get for non-ascii.
                // OBS for open api spec så funker ikke dette og feltene må annoteres med @JsonProperty
                .addModule(
                    KotlinModule.Builder().enable(KotlinFeature.KotlinPropertyNameAsImplicitName).build()
                )
                .propertyNamingStrategy(tools.jackson.databind.PropertyNamingStrategies.LOWER_CAMEL_CASE)
        }
    }
}
