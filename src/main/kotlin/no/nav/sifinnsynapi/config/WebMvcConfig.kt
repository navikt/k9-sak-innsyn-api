package no.nav.sifinnsynapi.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

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
}
