package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.security.token.support.spring.SpringTokenValidationContextHolder
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.utils.PSBJsonUtils
import no.nav.sifinnsynapi.util.personIdent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.util.*

@Service
@Retryable(
    noRetryFor = [
        HttpClientErrorException.Unauthorized::class,
        HttpClientErrorException.Forbidden::class,
        HttpClientErrorException.NotFound::class,
        LegacySøknadNotFoundException::class],
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
    private val tokenValidationContextHolder: SpringTokenValidationContextHolder,
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(LegacyInnsynApiService::class.java)

        val søknadUrl = UriComponentsBuilder
            .fromUriString("/soknad")
            .build()
            .toUriString()

        val søknadOpplysningerOppslagFeil = IllegalStateException("Feilet med henting av søknad fra sif-innsyn-api.")
    }

    fun hentLegacySøknad(søknadId: String): LegacySøknadDTO =
        hentLegacySøknad(søknadId, tokenValidationContextHolder.personIdent())

    private fun hentLegacySøknad(søknadId: String, fødselsnummer: String): LegacySøknadDTO {
        val cachedSøknad = legacySøknadRepository.finnForSøknadIdOgFødselsnummer(søknadId, fødselsnummer)
        if (cachedSøknad != null) {
            logger.info("Fant søknad med id=$søknadId i databasen, returnerer cached versjon")
            return cachedSøknad.toDTO(objectMapper)
        }

        val responseEntity = hentLegacySøknadViaRest(søknadId)

        return if (responseEntity.statusCode.is2xxSuccessful) {
            val søknadDTO = responseEntity.body!!
            if (søknadDTO.søkersFødselsnummer() != fødselsnummer) {
                logger.warn("Søknad med id=$søknadId tilhører ikke innlogget bruker")
                throw LegacySøknadNotFoundException(søknadId)
            }
            lagreSøknad(søknadDTO, søknadId)
            søknadDTO
        } else {
            logger.error(
                "Henting av søknadsdata feilet med status: {}, og respons: {}",
                responseEntity.statusCode,
                responseEntity.body
            )
            throw LegacySøknadNotFoundException(søknadId)
        }
    }

    fun hentLegacySøknader(søknadsIder: List<String>): List<LegacySøknadDTO> {
        logger.info("Henter ${søknadsIder.size} legacy søknader for søknadsIder: {}", søknadsIder)
        if (søknadsIder.isEmpty()) return listOf()
        val fødselsnummer = tokenValidationContextHolder.personIdent()
        val legacySøknaderFraDatabasen = legacySøknadRepository
            .finnAlleForSøknadIderOgFødselsnummer(søknadsIder, fødselsnummer)
            .map { it.toDTO(objectMapper) }

        // Diff mellom søknadsIder og de som finnes i databasen
        val eksisterendeIder = legacySøknaderFraDatabasen.map { it.søknadId.toString() }
        val manglendeIderFraDatabasen = søknadsIder.filterNot { eksisterendeIder.contains(it) }

        val legacySøknaderFraRest: List<LegacySøknadDTO> = if (manglendeIderFraDatabasen.isNotEmpty()) {
            logger.info("Mangler ${manglendeIderFraDatabasen.size} søknader i databasen, henter disse fra sif-innsyn-api")
            manglendeIderFraDatabasen.mapNotNull { søknadId ->
                runCatching { hentLegacySøknad(søknadId, fødselsnummer) }.getOrNull()
            }
        } else {
            listOf()
        }

        logger.info("Totalt hentet ${legacySøknaderFraDatabasen.size + legacySøknaderFraRest.size} legacy søknader")
        return legacySøknaderFraDatabasen + legacySøknaderFraRest
    }

    private fun hentLegacySøknadViaRest(søknadId: String): ResponseEntity<LegacySøknadDTO> {
        logger.info("Søknad med id=$søknadId finnes ikke i databasen, henter fra sif-innsyn-api")

        val exchange = sifInnsynClient.exchange(
            "$søknadUrl/$søknadId",
            HttpMethod.GET,
            null,
            LegacySøknadDTO::class.java
        )
        logger.info("Fikk response {} for oppslag av søknadsdata fra sif-innsyn-api", exchange.statusCode)
        return exchange
    }

    private fun lagreSøknad(søknadDTO: LegacySøknadDTO, søknadId: String) {
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
    }

    @Recover
    private fun recover(error: LegacySøknadNotFoundException, søknadId: String): LegacySøknadDTO {
        throw error
    }

    @Recover
    private fun recover(error: HttpClientErrorException.NotFound, søknadId: String): LegacySøknadDTO {
        throw LegacySøknadNotFoundException(søknadId)
    }

    @Recover
    private fun recover(error: HttpServerErrorException, søknadId: String): LegacySøknadDTO {
        logger.warn(
            "Kall for å hente søknad med id=$søknadId fra $søknadUrl feilet. Response body: {}",
            error.responseBodyAsString,
            error
        )
        throw søknadOpplysningerOppslagFeil
    }

    @Recover
    private fun recover(error: HttpClientErrorException, søknadId: String): LegacySøknadDTO {
        logger.warn(
            "Kall for å hente søknad med id=$søknadId fra $søknadUrl feilet. Response body: {}",
            error.responseBodyAsString,
            error
        )
        throw søknadOpplysningerOppslagFeil
    }
}

private fun LegacySøknadDTO.søkersFødselsnummer(): String? =
    (søknad[PSBJsonUtils.SØKER] as? Map<*, *>)?.get(PSBJsonUtils.FØDSELSNUMMER) as? String

fun LegacySøknadDAO.toDTO(objectMapper: ObjectMapper): LegacySøknadDTO {
    return LegacySøknadDTO(
        søknadId = UUID.fromString(this.søknadId),
        søknadstype = LegacySøknadstype.valueOf(this.søknadstype),
        søknad = objectMapper.readValue(this.søknad),
        saksId = this.saksId,
        journalpostId = this.journalpostId
    )
}
