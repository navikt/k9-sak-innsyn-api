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
    }

    fun hentAktørId(): OppslagRespons? {
        val exchange = oppslagsKlient.getForEntity(søkerUrl.toUriString(), OppslagRespons::class.java)
        logger.info("Fikk response {} fra oppslag", exchange.statusCode)

        return exchange.body
    }

    @Recover
    private fun recover(error: HttpServerErrorException): OppslagRespons? {
        logger.error("Error response = '${error.responseBodyAsString}' fra '${søkerUrl.toUriString()}'")
        throw IllegalStateException("Feil ved henting av søkers personinformasjon")
    }

    @Recover
    private fun recover(error: HttpClientErrorException): OppslagRespons? {
        logger.error("Error response = '${error.responseBodyAsString}' fra '${søkerUrl.toUriString()}'")
        throw IllegalStateException("Feil ved henting av søkers personinformasjon")
    }

    @Recover
    private fun recover(error: ResourceAccessException): OppslagRespons? {
        logger.error("{}", error.message)
        throw IllegalStateException("Timout ved henting av søkers personinformasjon")
    }
}

data class OppslagRespons(@JsonProperty("aktør_id") val aktør_id: String){
    override fun toString(): String {
        return "OppslagRespons(aktør_id='******')"
    }
}
