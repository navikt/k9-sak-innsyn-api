package no.nav.sifinnsynapi.oppslag

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
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
class OppslagsService(
    @Qualifier("k9OppslagsKlient")
    private val oppslagsKlient: RestTemplate
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(OppslagsService::class.java)

        val søkerUrl = UriComponentsBuilder
            .fromUriString("/meg")
            .queryParam("a", "aktør_id")
            .build()

        val barnUrl = UriComponentsBuilder
            .fromUriString("/meg")
            .queryParam("a", "barn[].aktør_id")
            .build()
    }

    fun hentAktørId(): SøkerOppslagRespons? {
        val exchange = oppslagsKlient.getForEntity(søkerUrl.toUriString(), SøkerOppslagRespons::class.java)
        logger.info("Fikk response {} fra oppslag", exchange.statusCode)

        return exchange.body
    }

    fun hentBarn(): List<BarnOppslagDTO> {
        val exchange = oppslagsKlient.getForEntity(barnUrl.toUriString(), BarnOppslagResponse::class.java)
        logger.info("Fikk response {} fra oppslag", exchange.statusCode)

        return exchange.body?.barn ?: listOf()
    }

    @Recover
    private fun recover(error: HttpServerErrorException): SøkerOppslagRespons? {
        logger.error("Error response = '${error.responseBodyAsString}' fra '${søkerUrl.toUriString()}'")
        throw IllegalStateException("Feil ved henting av søkers personinformasjon")
    }

    @Recover
    private fun recover(error: HttpClientErrorException): SøkerOppslagRespons? {
        logger.error("Error response = '${error.responseBodyAsString}' fra '${søkerUrl.toUriString()}'")
        throw IllegalStateException("Feil ved henting av søkers personinformasjon")
    }

    @Recover
    private fun recover(error: ResourceAccessException): SøkerOppslagRespons? {
        logger.error("{}", error.message)
        throw IllegalStateException("Timout ved henting av søkers personinformasjon")
    }

    @Recover
    private fun recoverBarn(error: HttpServerErrorException): List<BarnOppslagDTO> {
        logger.error("Error response = '${error.responseBodyAsString}' fra '${søkerUrl.toUriString()}'")
        throw IllegalStateException("Feil ved henting av søkers barn")
    }

    @Recover
    private fun recoverBarn(error: HttpClientErrorException): List<BarnOppslagDTO> {
        logger.error("Error response = '${error.responseBodyAsString}' fra '${søkerUrl.toUriString()}'")
        throw IllegalStateException("Feil ved henting av søkers barn")
    }

    @Recover
    private fun recoverBarn(error: ResourceAccessException): List<BarnOppslagDTO> {
        logger.error("{}", error.message)
        throw IllegalStateException("Timout ved henting av søkers barn")
    }
}

data class SøkerOppslagRespons(@JsonProperty("aktør_id") val aktør_id: String) {
    override fun toString(): String {
        return "SøkerOppslagRespons(aktør_id='******')"
    }
}

private data class BarnOppslagResponse(val barn: List<BarnOppslagDTO>)

data class BarnOppslagDTO(
    @JsonProperty("aktør_id") val aktør_id: String
) {
    override fun toString(): String {
        return "BarnOppslagDTO(aktør_id='******', identitetsnummer=******)"
    }
}
