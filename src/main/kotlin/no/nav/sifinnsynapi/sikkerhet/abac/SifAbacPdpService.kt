package no.nav.sifinnsynapi.sikkerhet.abac

import no.nav.sif.abac.kontrakt.abac.dto.PersonerOperasjonDto
import no.nav.sif.abac.kontrakt.abac.resultat.Tilgangsbeslutning
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForObject

/** Spør sif-abac-pdp om innlogget saksbehandler har tilgang. */
@Service
@Retryable(
    noRetryFor = [HttpClientErrorException.Unauthorized::class, HttpClientErrorException.Forbidden::class, ResourceAccessException::class],
    backoff = Backoff(
        delayExpression = "\${spring.rest.retry.initialDelay}",
        multiplierExpression = "\${spring.rest.retry.multiplier}",
        maxDelayExpression = "\${spring.rest.retry.maxDelay}"
    ),
    maxAttemptsExpression = "\${spring.rest.retry.maxAttempts}",
)
class SifAbacPdpService(
    @Qualifier("sifAbacPdpKlient") private val sifAbacPdpKlient: RestTemplate,
) {
    private companion object {
        const val PERSONER_PATH = "/tilgangskontroll/v2/k9/personer"
    }

    fun sjekkTilgangTilPersonerForInnloggetBruker(forespørsel: PersonerOperasjonDto): Tilgangsbeslutning =
        sifAbacPdpKlient.postForObject<Tilgangsbeslutning>(PERSONER_PATH, forespørsel)
            ?: throw IllegalStateException("Tomt svar fra sif-abac-pdp")
}
