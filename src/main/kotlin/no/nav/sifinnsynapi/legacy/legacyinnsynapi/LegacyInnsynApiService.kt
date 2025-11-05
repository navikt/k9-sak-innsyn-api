package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
    noRetryFor = [
        HttpClientErrorException.Unauthorized::class,
        HttpClientErrorException.Forbidden::class,
        HttpClientErrorException.NotFound::class,
        ResourceAccessException::class],
    backoff = Backoff(
        delayExpression = "\${spring.rest.retry.initialDelay}",
        multiplierExpression = "\${spring.rest.retry.multiplier}",
        maxDelayExpression = "\${spring.rest.retry.maxDelay}"
    ),
    maxAttemptsExpression = "\${spring.rest.retry.maxAttempts}"
)
class LegacyInnsynApiService(
    @Qualifier("sifInnsynApiClient")
    private val sifInnsynClient: RestTemplate,
    private val legacySøknadRepository: LegacySøknadRepository,
    private val objectMapper: ObjectMapper,
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
        // Sjekk om søknaden finnes i databasen først
        val cachedSøknad = legacySøknadRepository.findById(søknadId)
        if (cachedSøknad.isPresent) {
            logger.info("Fant søknad med id=$søknadId i databasen, returnerer cached versjon")
            return cachedSøknad.get().toDTO(objectMapper)
        }

        logger.info("Søknad med id=$søknadId finnes ikke i databasen, henter fra sif-innsyn-api")

        val exchange = sifInnsynClient.exchange(
            "$søknadUrl/$søknadId",
            HttpMethod.GET,
            null,
            LegacySøknadDTO::class.java
        )
        logger.info("Fikk response {} for oppslag av søknadsdata fra sif-innsyn-api", exchange.statusCode)

        return if (exchange.statusCode.is2xxSuccessful) {
            val søknadDTO = exchange.body!!

            // Lagre i databasen for fremtidige kall
            try {
                val dao = LegacySøknadDAO(
                    søknadId = søknadDTO.søknadId.toString(),
                    søknadstype = søknadDTO.søknadstype.name,
                    søknad = objectMapper.writeValueAsString(søknadDTO.søknad),
                    saksId = søknadDTO.saksId,
                    journalpostId = søknadDTO.journalpostId
                )
                legacySøknadRepository.save(dao)
                logger.info("Lagret søknad med id=$søknadId i databasen")
            } catch (e: Exception) {
                logger.warn("Kunne ikke lagre søknad med id=$søknadId i databasen", e)
                // Vi fortsetter selv om lagring feiler
            }

            søknadDTO
        } else {
            logger.error(
                "Henting av søknadsdata feilet med status: {}, og respons: {}",
                exchange.statusCode,
                exchange.body
            )
            throw LegacySøknadNotFoundException(søknadId)
        }
    }

    @Recover
    private fun recover(error: HttpServerErrorException, søknadId: String): LegacySøknadDTO {
        logger.warn("Kall for å hente søknad med id=$søknadId fra $søknadUrl feilet. Response body: {}", error.responseBodyAsString, error)
        throw søknadOpplysningerOppslafFeil
    }

    @Recover
    private fun recover(error: HttpClientErrorException, søknadId: String): LegacySøknadDTO {
        logger.warn("Kall for å hente søknad med id=$søknadId fra $søknadUrl feilet. Response body: {}", error.responseBodyAsString, error)
        throw søknadOpplysningerOppslafFeil
    }

    @Recover
    private fun recover(error: ResourceAccessException, søknadId: String): LegacySøknadDTO {
        logger.warn("Kall for å hente søknad med id=$søknadId fra $søknadUrl feilet.", error)
        throw søknadOpplysningerOppslafFeil
    }
}

fun LegacySøknadDAO.toDTO(objectMapper: ObjectMapper): LegacySøknadDTO {
    return LegacySøknadDTO(
        søknadId = java.util.UUID.fromString(this.søknadId),
        søknadstype = LegacySøknadstype.valueOf(this.søknadstype),
        søknad = objectMapper.readValue(this.søknad),
        saksId = this.saksId,
        journalpostId = this.journalpostId
    )
}
