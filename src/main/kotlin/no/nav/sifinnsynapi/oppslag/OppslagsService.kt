package no.nav.sifinnsynapi.oppslag

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate

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
    private val oppslagsKlient: RestTemplate,
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(OppslagsService::class.java)

        val søkerUrl = UriComponentsBuilder
            .fromUriString("/meg")
            .queryParam("a", "aktør_id")
            .build()

        val barnUrl = UriComponentsBuilder
            .fromUriString("/meg")
            .queryParam(
                "a",
                "barn[].aktør_id",
                "barn[].fornavn",
                "barn[].mellomnavn",
                "barn[].etternavn",
                "barn[].fødselsdato",
                "barn[].identitetsnummer"
            )
            .build()

        val identerUrl = UriComponentsBuilder
            .fromUriString("/system/hent-identer")
            .build()

        val systemBarnUrl = UriComponentsBuilder
            .fromUriString("/system/hent-barn")
            .build()
    }

    fun hentSøker(): SøkerOppslagRespons? {
        logger.info("Slår opp søker...")
        val exchange = oppslagsKlient.getForEntity(søkerUrl.toUriString(), SøkerOppslagRespons::class.java)
        logger.info("Fikk response {} for oppslag av søker.", exchange.statusCode)

        return exchange.body
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
        throw IllegalStateException("Timeout ved henting av søkers personinformasjon")
    }

    fun hentBarn(): List<BarnOppslagDTO> {
        logger.info("Slår opp barn...")
        val exchange = oppslagsKlient.getForEntity(barnUrl.toUriString(), BarnOppslagResponse::class.java)
        logger.info("Fikk response {} fra oppslag av barn.", exchange.statusCode)

        return exchange.body?.barn ?: listOf()
    }

    @Recover
    private fun recoverBarn(error: HttpServerErrorException): List<BarnOppslagDTO> {
        logger.error("Error response = '${error.responseBodyAsString}' fra '${barnUrl.toUriString()}'")
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

    fun hentIdenter(hentIdenterForespørsel: HentIdenterForespørsel): List<HentIdenterRespons> {
        return kotlin.runCatching {
            logger.info("Henter identer...")
            oppslagsKlient.exchange(
                identerUrl.toUriString(),
                HttpMethod.POST,
                HttpEntity(hentIdenterForespørsel),
                object : ParameterizedTypeReference<List<HentIdenterRespons>>() {})
        }.fold(
            onSuccess = { response: ResponseEntity<List<HentIdenterRespons>> ->
                logger.info("Fikk response {} for oppslag av hentIdenter.", response.statusCode)
                response.body!!
            },
            onFailure = { error: Throwable ->
                if (error is RestClientException) {
                    logger.error("Feilet ved henting av identer. Feilmelding: {}", error.message)
                }
                throw error
            }
        )
    }

    @Recover
    private fun recoverHentIdenter(error: HttpServerErrorException): List<HentIdenterRespons> {
        logger.error("Error response = '${error.responseBodyAsString}' fra '${identerUrl.toUriString()}'")
        throw IllegalStateException("Feil ved henting av identer.")
    }

    @Recover
    private fun recoverHentIdenter(error: HttpClientErrorException): List<HentIdenterRespons> {
        logger.error("Error response = '${error.responseBodyAsString}' fra '${identerUrl.toUriString()}'")
        throw IllegalStateException("Feil ved henting av identer.")
    }

    @Recover
    private fun recoverHentIdenter(error: RestClientException): List<HentIdenterRespons> {
        logger.error("{}", error.message)
        throw IllegalStateException("Feil ved henting av identer.")
    }

    fun systemoppslagBarn(hentBarnForespørsel: HentBarnForespørsel): List<BarnOppslagDTO> {
        logger.info("Henter ${hentBarnForespørsel.identer.size} barn ved systemoppslag...")
        val exchange = oppslagsKlient.exchange(
            systemBarnUrl.toUriString(),
            HttpMethod.POST,
            HttpEntity(hentBarnForespørsel),
            object : ParameterizedTypeReference<List<SystemoppslagBarn>>() {})

        val barnOppslagDTOS = exchange.body?.map {
            BarnOppslagDTO(
                fødselsdato = it.pdlBarn.fødselsdato,
                fornavn = it.pdlBarn.fornavn,
                mellomnavn = it.pdlBarn.mellomnavn,
                etternavn = it.pdlBarn.etternavn,
                aktørId = it.aktørId.value,
                identitetsnummer = it.pdlBarn.ident.value,
                adressebeskyttelse = it.pdlBarn.adressebeskyttelse
            )
        }

        if (barnOppslagDTOS.isNullOrEmpty()) {
            logger.info("Fant ingen barn ved systemoppslag.")
        }

        val (ikkeAdressebeskyttet, adressebeskyttet) = (barnOppslagDTOS ?: listOf()).partition { it.ikkeErAdressebeskyttet() }
        if (adressebeskyttet.isNotEmpty()) {
            logger.info("Filtererte ut ${adressebeskyttet.size} barn med adressebeskyttelse.")
        }

        return ikkeAdressebeskyttet
    }

    @Recover
    fun systemoppslagBarn(
        hentBarnForespørsel: HentBarnForespørsel,
        error: HttpServerErrorException,
    ): List<BarnOppslagDTO> {
        logger.error("Error response = '${error.responseBodyAsString}' fra '${systemBarnUrl.toUriString()}'")
        throw IllegalStateException("Feil ved systemoppslag av barn")
    }

    @Recover
    fun systemoppslagBarn(
        hentBarnForespørsel: HentBarnForespørsel,
        error: HttpClientErrorException,
    ): List<BarnOppslagDTO> {
        logger.error("Error response = '${error.responseBodyAsString}' fra '${systemBarnUrl.toUriString()}'")
        throw IllegalStateException("Feil ved systemoppslag av barn")
    }

    @Recover
    fun systemoppslagBarn(
        hentBarnForespørsel: HentBarnForespørsel,
        error: ResourceAccessException,
    ): List<BarnOppslagDTO> {
        logger.error("'${error.message}' ved systemkall mot '${systemBarnUrl.toUriString()}'")
        throw IllegalStateException("Feil ved systemoppslag av barn")
    }
}

data class SøkerOppslagRespons(
    @JsonAlias("aktør_id") val aktørId: String,

    ) {
    override fun toString(): String {
        return "SøkerOppslagRespons(aktør_id='******')"
    }
}

private data class BarnOppslagResponse(val barn: List<BarnOppslagDTO>)
data class HentIdenterForespørsel(
    val identer: List<String>,
    val identGrupper: List<IdentGruppe>,
)

data class HentBarnForespørsel(
    val identer: List<String>,
)

data class HentIdenterRespons(
    val ident: String,
    val identer: List<Ident>,
)

data class Ident(val ident: String, val gruppe: IdentGruppe)

enum class IdentGruppe {
    AKTORID, FOLKEREGISTERIDENT, NPID
}

data class BarnOppslagDTO(
    val fødselsdato: LocalDate,
    val fornavn: String,
    val mellomnavn: String? = null,
    val etternavn: String,
    @JsonAlias("aktør_id") val aktørId: String,
    val identitetsnummer: String? = null,
    internal @JsonIgnore val adressebeskyttelse: List<Adressebeskyttelse> = emptyList(),
) {
    override fun toString(): String {
        return "BarnOppslagDTO(fødselsdato='******', fornavn='******', mellomnavn='******', etternavn='******', aktør_id='******', identitetsnummer='******')"
    }

    fun ikkeErAdressebeskyttet(): Boolean {
        return (adressebeskyttelse.isEmpty()) || (adressebeskyttelse.none {
            it.gradering == AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND
                    || it.gradering == AdressebeskyttelseGradering.STRENGT_FORTROLIG
                    || it.gradering == AdressebeskyttelseGradering.FORTROLIG
        })
    }
}

data class Adressebeskyttelse(
    val gradering: AdressebeskyttelseGradering,
)

enum class AdressebeskyttelseGradering {
    STRENGT_FORTROLIG_UTLAND, STRENGT_FORTROLIG, FORTROLIG, UGRADERT
}

data class SystemoppslagBarn(
    val aktørId: PdlBarnIdent,
    val pdlBarn: PdlBarn,
)

data class PdlBarn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val forkortetNavn: String?,
    val fødselsdato: LocalDate,
    val ident: PdlBarnIdent,
    val adressebeskyttelse: List<Adressebeskyttelse> = emptyList(),
) {
    override fun toString(): String {
        return "PdlBarn(fornavn='$*****', mellomnavn='$*****', etternavn='$*****', forkortetNavn='$*****', fødselsdato=$*****, ident=$ident)"
    }
}

data class PdlBarnIdent(val value: String) {
    override fun toString(): String {
        return "PdlBarnIdent(value='******')"
    }
}

class Organisasjon(
    val organisasjonsnummer: String,
    val navn: String?,
)
