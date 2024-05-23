package no.nav.sifinnsynapi.k9sak

import com.fasterxml.jackson.annotation.JsonFormat
import no.nav.k9.sak.typer.Saksnummer
import no.nav.sifinnsynapi.common.AktørId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import org.springframework.http.HttpEntity
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
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import reactor.core.publisher.Mono
import java.net.URI
import java.time.LocalDate

@Service
@Retryable(
    noRetryFor = [K9SakException::class, HttpClientErrorException.Unauthorized::class, HttpClientErrorException.Forbidden::class, ResourceAccessException::class],
    backoff = Backoff(
        delayExpression = "\${spring.rest.retry.initialDelay}",
        multiplierExpression = "\${spring.rest.retry.multiplier}",
        maxDelayExpression = "\${spring.rest.retry.maxDelay}"
    ),
    maxAttemptsExpression = "\${spring.rest.retry.maxAttempts}",

    )
class K9SakService(
    @Qualifier("k9SakKlient")
    private val k9SakKlient: RestTemplate,
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(K9SakService::class.java)

        private val hentSisteGyldigeVedtakForAktorIdUrl = "/api/brukerdialog/omsorgsdager-kronisk-sykt-barn/har-gyldig-vedtak"
    }

    fun hentSisteGyldigeVedtakForAktorId(
        inputDto: HentSisteGyldigeVedtakForAktorIdDto
    ): HentSisteGyldigeVedtakForAktorIdResponse? {
        val httpEntity = HttpEntity(inputDto)
        val response = k9SakKlient.exchange(
            hentSisteGyldigeVedtakForAktorIdUrl,
            HttpMethod.POST,
            httpEntity,
            HentSisteGyldigeVedtakForAktorIdResponse::class.java
        )
        return response.body
    }

    @Recover
    private fun hentSisteGyldigeVedtakForAktorId(
        exception: HttpClientErrorException,
        inputDto: HentSisteGyldigeVedtakForAktorIdDto
    ): HentSisteGyldigeVedtakForAktorIdResponse? {
        logger.error("Fikk en HttpClientErrorException når man kalte hentSisteGyldigeVedtakForAktorId tjeneste i k9-sak. Error response = '${exception.responseBodyAsString}'")
        val message = exception.responseBodyAsString.ifEmpty { exception.message.orEmpty() }
        throw K9SakException(message, HttpStatus.valueOf(exception.statusCode.value()))
    }

    @Recover
    private fun hentSisteGyldigeVedtakForAktorId(
        exception: HttpServerErrorException,
        inputDto: HentSisteGyldigeVedtakForAktorIdDto
    ): HentSisteGyldigeVedtakForAktorIdResponse? {
        logger.error("Fikk en HttpServerErrorException når man kalte hentSisteGyldigeVedtakForAktorId tjeneste i k9-sak.")
        val message = exception.responseBodyAsString.ifEmpty { exception.message.orEmpty() }
        throw K9SakException(message, HttpStatus.valueOf(exception.statusCode.value()))
    }

    @Recover
    private fun hentSisteGyldigeVedtakForAktorId(
        exception: ResourceAccessException,
        inputDto: HentSisteGyldigeVedtakForAktorIdDto
    ): HentSisteGyldigeVedtakForAktorIdResponse? {
        logger.error("Fikk en ResourceAccessException når man kalte hentSisteGyldigeVedtakForAktorId tjeneste i k9-sak.")
        val message = exception.message.orEmpty()
        throw K9SakException(message, HttpStatus.INTERNAL_SERVER_ERROR)
    }
}

data class HentSisteGyldigeVedtakForAktorIdDto(
    val aktørId: AktørId,
    val pleietrengendeAktørId: AktørId
)

data class HentSisteGyldigeVedtakForAktorIdResponse(
    val harInnvilgedeBehandlinger: Boolean,
    val saksnummer: Saksnummer?,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val vedtaksdato: LocalDate?
)

class K9SakException(
    melding: String,
    httpStatus: HttpStatus
) : ErrorResponseException(httpStatus, asProblemDetail(melding, httpStatus), null) {
    private companion object {
        private fun asProblemDetail(
            melding: String,
            httpStatus: HttpStatus,
        ): ProblemDetail {
            val problemDetail = ProblemDetail.forStatus(httpStatus)
            problemDetail.title = "Feil ved kall mot k9-sak"
            problemDetail.detail = melding

            problemDetail.type = URI("/problem-details/k9-sak")

            return problemDetail
        }
    }
}
