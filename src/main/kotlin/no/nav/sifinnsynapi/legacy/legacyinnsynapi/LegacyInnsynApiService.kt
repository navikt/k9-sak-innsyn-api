package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpMethod
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Service
@Retryable(
    exclude = [HttpClientErrorException.Unauthorized::class, HttpClientErrorException.Forbidden::class, ResourceAccessException::class],
    backoff = Backoff(
        delayExpression = "\${spring.rest.retry.initialDelay}",
        multiplierExpression = "\${spring.rest.retry.multiplier}",
        maxDelayExpression = "\${spring.rest.retry.maxDelay}"
    ),
    maxAttemptsExpression = "\${spring.rest.retry.maxAttempts}"
)
class LegacyInnsynApiService(
    @Qualifier("sifInnsynApiClient")
    private val sifInnsynClient: RestTemplate
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(LegacyInnsynApiService::class.java)

        val søknadUrl = UriComponentsBuilder
            .fromUriString("/soknad")
            .build()
            .toUriString()

        val søknadOpplysningerOppslafFeil = IllegalStateException("Feilet med henting av søknad fra sif-innsyn-api.")
    }

    fun hentLegacySøknad(søknadId: String): LegacySøknadDTO {
        val exchange = sifInnsynClient.exchange(
            "$søknadUrl/$søknadId",
            HttpMethod.GET,
            null,
            LegacySøknadDTO::class.java
        )
        logger.info("Fikk response {} for oppslag av søknadsdata fra sif-innsyn-api", exchange.statusCode)

        return if (exchange.statusCode.is2xxSuccessful) {
            exchange.body!!
        } else {
            logger.error(
                "Henting av søknadsdata feilet med status: {}, og respons: {}",
                exchange.statusCode,
                exchange.body
            )
            throw IllegalStateException("Feilet med henting av søknadsdata fra sif-innsyn-api")
        }
    }

    @Recover
    private fun recover(error: HttpServerErrorException): List<LegacySøknadDTO> {
        logger.error("Error response = '${error.responseBodyAsString}' fra '$søknadUrl'")
        throw søknadOpplysningerOppslafFeil
    }

    @Recover
    private fun recover(error: HttpClientErrorException): List<LegacySøknadDTO> {
        logger.error("Error response = '${error.responseBodyAsString}' fra '$søknadUrl'")
        throw søknadOpplysningerOppslafFeil
    }

    @Recover
    private fun recover(error: ResourceAccessException): List<LegacySøknadDTO> {
        logger.error("{}", error.message)
        throw søknadOpplysningerOppslafFeil
    }
}
