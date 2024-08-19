package no.nav.sifinnsynapi.sikkerhet

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class AuthorizationService(
    private val contextHolder: ContextHolder,
    private val k9DriftGruppeId: String,
) {
    fun harTilgangTilDriftRolle(): Boolean {
        return contextHolder.requestKontekst()?.jwtToken?.containsClaim("groups", k9DriftGruppeId) ?: false
    }
}

@Configuration
class AuthorizationConfig(
    @Value("\${no.nav.security.k9-drift-gruppe}") private val k9DriftGruppeId: String,
) {
    @Bean
    fun authorizationService() = AuthorizationService(ContextHolder.INSTANCE, k9DriftGruppeId)
}
