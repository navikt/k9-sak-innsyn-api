package no.nav.sifinnsynapi.config

import io.swagger.models.Scheme
import org.springframework.context.EnvironmentAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket

@Configuration
@Profile("local", "dev-gcp")
class SwaggerConfiguration : EnvironmentAware {
    private var env: Environment? = null

    @Bean
    fun api(): Docket {
        return Docket(DocumentationType.SWAGGER_2)
                .protocols(setOf(Scheme.HTTP.toValue(), Scheme.HTTPS.toValue()))
                .select()
                .apis(RequestHandlerSelectors.basePackage("no.nav.sifinnsynapi"))
                .build()
    }

    override fun setEnvironment(env: Environment) {
        this.env = env
    }
}
