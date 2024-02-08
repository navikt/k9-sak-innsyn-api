package no.nav.sifinnsynapi.dokumentoversikt

import jakarta.validation.constraints.Pattern
import no.nav.security.token.support.core.api.Protected
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.sifinnsynapi.Routes.DOKUMENT
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@ProtectedWithClaims(issuer = "selvbetjening", claimMap = ["acr=Level4"])
@Validated
class DokumentController(
    private val dokumentService: DokumentService,
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(DokumentController::class.java)
    }

    @GetMapping(
        "$DOKUMENT/{journalpostId}/{dokumentInfoId}/{variantFormat}",
        produces = [MediaType.APPLICATION_PDF_VALUE]
    )
    @Protected
    @ResponseStatus(HttpStatus.OK)
    fun hentDokument(
        @PathVariable @Pattern(regexp = "\\d{9}", message = "[\${validatedValue}] matcher ikke tillatt pattern [{regexp}]") journalpostId: String,
        @PathVariable @Pattern(regexp = "\\d{9}", message = "[\${validatedValue}] matcher ikke tillatt pattern [{regexp}]") dokumentInfoId: String,
        @PathVariable @Pattern(regexp = "ARKIV", message = "[\${validatedValue}] matcher ikke tillatt pattern [{regexp}]") variantFormat: String,
        @RequestParam dokumentTittel: String,
    ): ResponseEntity<Resource> {
        logger.info("Henter dokument for journalpostId: {}", journalpostId)

        val dokument = dokumentService.hentDokument(journalpostId, dokumentInfoId, variantFormat)
        val resource = ByteArrayResource(dokument.body)

        val formatertFilnavn = filnavn(dokumentTittel)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=$formatertFilnavn")
            .contentLength(resource.byteArray.size.toLong())
            .body(resource)
    }
}

/**
 * Erstatter filtype på dokumentTittel med contentType fra hentet dokument.
 *
 */
fun filnavn(dokumentTittel: String): String =
    dokumentTittel.replaceAfterLast(".", "pdf")
