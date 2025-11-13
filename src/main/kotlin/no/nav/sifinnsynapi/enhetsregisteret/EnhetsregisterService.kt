package no.nav.sifinnsynapi.enhetsregisteret

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.ErrorResponseException
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.net.URI

/**
 * Service for å hente organisasjonsinfo fra enhetsregisteret (ereg).
 *
 * Se [EREG API V1](https://ereg-services.dev.intern.nav.no/swagger-ui/index.html) for mer informasjon.
 */

@Retryable(
    noRetryFor = [
        EnhetsregisterException::class
    ],
    backoff = Backoff(
        delayExpression = "\${spring.rest.retry.initialDelay}",
        multiplierExpression = "\${spring.rest.retry.multiplier}",
        maxDelayExpression = "\${spring.rest.retry.maxDelay}"
    ),
    maxAttemptsExpression = "\${spring.rest.retry.maxAttempts}",
)
@Service
class EnhetsregisterService(
    @Qualifier("enhetsregisterKlient") private val enhetsregisterKlient: RestTemplate,
    private val objectMapper: ObjectMapper,
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(EnhetsregisterService::class.java)

        const val TJENESTE_NAVN = "enhetsregisteret"

        private val hentOrganisasjonInfoUrl = "/v2/organisasjon"
    }

    fun hentOrganisasjonsinfo(organisasjonsnummer: String): OrganisasjonRespons {
        return runCatching {
            enhetsregisterKlient.exchange(
                "$hentOrganisasjonInfoUrl/$organisasjonsnummer",
                HttpMethod.GET,
                null,
                OrganisasjonRespons::class.java
            )
        }.fold(
            onSuccess = { it.body!! },
            onFailure = {
                if (it is HttpClientErrorException.NotFound) {
                    throw EnhetsregisterException(
                        "Fant ikke organisasjon med organisasjonsnummer: $organisasjonsnummer",
                        HttpStatus.NOT_FOUND
                    )
                }
                throw it
            }
        )
    }

    @Recover
    open fun hentOrganisasjonsinfo(ex: HttpClientErrorException): OrganisasjonRespons {
        val feilmelding = parseFeilmelding(ex.responseBodyAsString)
        logger.warn("Klientfeil ${ex.statusCode} mot $TJENESTE_NAVN: $feilmelding", ex)

        throw EnhetsregisterException(feilmelding, HttpStatus.valueOf(ex.statusCode.value()))
    }

    @Recover
    open fun recoverServerError(ex: HttpServerErrorException): OrganisasjonRespons {
        val feilmelding = parseFeilmelding(ex.responseBodyAsString)
        logger.error("Serverfeil ${ex.statusCode} mot $TJENESTE_NAVN: $feilmelding", ex)
        throw EnhetsregisterException("Annen feil: $feilmelding", HttpStatus.valueOf(ex.statusCode.value()))
    }

    private fun parseFeilmelding(body: String): String =
        try {
            objectMapper.readTree(body).get("melding")?.asText() ?: body
        } catch (_: Exception) {
            body
        }
}

class EnhetsregisterException(
    melding: String,
    httpStatus: HttpStatus,
) : ErrorResponseException(httpStatus, asProblemDetail(melding, httpStatus), null) {
    private companion object {
        private fun asProblemDetail(
            melding: String,
            httpStatus: HttpStatus,
        ): ProblemDetail {
            val problemDetail = ProblemDetail.forStatus(httpStatus)
            problemDetail.title = "Feil ved kall mot enhetsregisteret"
            problemDetail.detail = melding

            problemDetail.type = URI("/problem-details/enhetsregisteret")

            return problemDetail
        }
    }
}
