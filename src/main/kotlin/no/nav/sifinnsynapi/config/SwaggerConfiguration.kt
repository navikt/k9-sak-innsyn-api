package no.nav.sifinnsynapi.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.OAuthFlow
import io.swagger.v3.oas.models.security.OAuthFlows
import io.swagger.v3.oas.models.security.Scopes
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.EnvironmentAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.net.URI
import java.util.Map


@Configuration
class SwaggerConfiguration(
    @Value("\${APPLICATION_INGRESS}") private val applicationIngress: URI,
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
            .components(getComponents())
            .addSecurityItem(SecurityRequirement().addList("Password Flow"))
    }

    private fun getComponents(): Components {
        val passwordFlowScheme: SecurityScheme = SecurityScheme()
            .type(SecurityScheme.Type.OAUTH2)
            .flows(
                OAuthFlows()
                    .password(
                        OAuthFlow()
                            .tokenUrl("http://localhost:8080/oauth/token")
                            .scopes(Scopes().addString("trust", "trust all"))
                    )
            )
        return Components()
            .securitySchemes(Map.of("Password Flow", passwordFlowScheme))
    }

    override fun setEnvironment(env: Environment) {
        this.env = env
    }
}
