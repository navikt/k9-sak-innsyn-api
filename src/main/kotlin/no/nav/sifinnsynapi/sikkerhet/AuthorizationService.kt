package no.nav.sifinnsynapi.sikkerhet

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class AuthorizationService(
    private val contextHolder: ContextHolder,
    private val k9DriftGruppeId: String,
    private val k9SakClientId: String,
) {
    fun harTilgangTilDriftRolle(): Boolean {
        return contextHolder.requestKontekst()?.jwtToken?.containsClaim("groups", k9DriftGruppeId) ?: false
    }

    // azp er client-id-en til appen som ba om tokenet. azp_name er ikke unik og skal ikke brukes til autorisasjon.
    fun kallErFraK9Sak(): Boolean {
        return contextHolder.requestKontekst()?.jwtToken?.jwtTokenClaims?.getStringClaim("azp") == k9SakClientId
    }
}

@Configuration
class AuthorizationConfig(
    @Value("\${no.nav.security.k9-drift-gruppe}") private val k9DriftGruppeId: String,
    @Value("\${no.nav.security.azure-pre-authorized-apps}") private val preAuthorizedApps: String,
    @Value("\${no.nav.security.k9-sak-azure-app-navn}") private val k9SakAppNavn: String,
) {
    @Bean
    fun authorizationService() = AuthorizationService(
        contextHolder = ContextHolder.INSTANCE,
        k9DriftGruppeId = k9DriftGruppeId,
        k9SakClientId = clientIdForPreAutorisertApp(k9SakAppNavn, preAuthorizedApps),
    )
}

/**
 * Finner client-id-en til en app i AZURE_APP_PRE_AUTHORIZED_APPS, som Nais lager fra inboundRules.
 * Feiler ved oppstart hvis appen mangler, så feil oppsett ikke gir et endepunkt som avviser alle kall.
 */
internal fun clientIdForPreAutorisertApp(appNavn: String, preAuthorizedApps: String): String =
    ObjectMapper().readTree(preAuthorizedApps)
        .firstOrNull { it.path("name").asText() == appNavn }
        ?.path("clientId")?.asText()
        ?.takeIf { it.isNotBlank() }
        ?: error("Fant ikke $appNavn i AZURE_APP_PRE_AUTHORIZED_APPS")
