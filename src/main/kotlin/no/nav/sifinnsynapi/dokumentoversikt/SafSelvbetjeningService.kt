package no.nav.sifinnsynapi.dokumentoversikt

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.constraints.Pattern
import no.nav.security.token.support.spring.SpringTokenValidationContextHolder
import no.nav.sifinnsynapi.safselvbetjening.generated.HentDokumentOversikt
import no.nav.sifinnsynapi.safselvbetjening.generated.hentdokumentoversikt.Dokumentoversikt
import no.nav.sifinnsynapi.util.personIdent
import org.slf4j.LoggerFactory
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import org.springframework.web.client.RestTemplate

@Service
class SafSelvbetjeningService(
    private val objectMapper: ObjectMapper,
    private val safSelvbetjeningRestTemplate: RestTemplate,
    private val safSelvbetjeningGraphQLClient: GraphQLWebClient,
    private val tokenValidationContextHolder: SpringTokenValidationContextHolder
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(SafSelvbetjeningService::class.java)
    }

    suspend fun hentDokumentoversikt(): Dokumentoversikt {
        val personIdent = tokenValidationContextHolder.personIdent()
        val response = safSelvbetjeningGraphQLClient.execute(
            HentDokumentOversikt(
                HentDokumentOversikt.Variables(personIdent)
            )
        )

        return when {
            response.data != null -> response.data!!.dokumentoversiktSelvbetjening

            !response.errors.isNullOrEmpty() -> {
                val errorSomJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response.errors)
                logger.error("Feil ved henting av dokumentoversikt. Årsak: {}", errorSomJson)
                throw IllegalStateException("Feil ved henting av dokumentoversikt.")
            }
            else -> error("Feil ved henting av dokumentoversikt.")
        }
    }

    @Validated
    fun hentDokument(
        @Pattern(regexp = "\\d{9}", message = "[\${validatedValue}] matcher ikke tillatt pattern [{regexp}]") journalpostId: String,
        @Pattern(regexp = "\\d{9}", message = "[\${validatedValue}] matcher ikke tillatt pattern [{regexp}]") dokumentInfoId: String,
        @Pattern(regexp = "ARKIV", message = "[\${validatedValue}] matcher ikke tillatt pattern [{regexp}]") variantFormat: String,
    ): ArkivertDokument {
        val response = safSelvbetjeningRestTemplate.exchange(
            "/rest/hentdokument/${journalpostId}/${dokumentInfoId}/${variantFormat}",
            HttpMethod.GET,
            null,
            ByteArray::class.java
        )

        return when {
            response.statusCode.is2xxSuccessful -> ArkivertDokument(
                body = response.body!!,
                contentType = response.headers.contentType!!.type,
                contentDisposition = response.headers.contentDisposition
            )
            else -> {
                logger.error("Feilet med å hente dokument. Response: {}", response)
                throw IllegalStateException("Feilet med å hente dokument.")
            }
        }
    }
}

data class ArkivertDokument(
    val body: ByteArray,
    val contentType: String,
    val contentDisposition: ContentDisposition
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArkivertDokument

        if (!body.contentEquals(other.body)) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = body.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}
