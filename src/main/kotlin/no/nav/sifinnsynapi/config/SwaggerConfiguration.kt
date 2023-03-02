package no.nav.sifinnsynapi.config

import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.EnvironmentAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.net.URI

@Configuration
class SwaggerConfiguration(
    @Value("\${APPLICATION_INGRESS}") private val applicationIngress: URI
) : EnvironmentAware {
    private var env: Environment? = null

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .addServersItem(
                Server().url(applicationIngress.toString()).description("Swagger Server")
            )
            .info(
                Info()
                    .title("K9 Sak Innsyn Api")
                    .description("API spesifikasjon for k9-sak-innsyn-api")
                    .version("v1.0.0")
            )
            .externalDocs(
                ExternalDocumentation()
                    .description("K9 Sak Innsyn Api GitHub repository")
                    .url("https://github.com/navikt/k9-sak-innsyn-api")
            )
    }

    override fun setEnvironment(env: Environment) {
        this.env = env
    }
}
