package no.nav.sifinnsynapi.soknad

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.RequiredIssuers
import no.nav.sifinnsynapi.Routes.SØKNAD
import no.nav.sifinnsynapi.config.Issuers
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequiredIssuers(
    ProtectedWithClaims(issuer = Issuers.TOKEN_X, claimMap = ["acr=Level4"])
)
class SøknadController(
    private val innsendingService: InnsendingService
) {
    companion object {
        val logger = LoggerFactory.getLogger(SøknadController::class.java)
    }

    @GetMapping(SØKNAD, produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(OK)
    fun hentSøknader(): List<SøknadDTO> {
        logger.info("Forsøker å hente søknadsopplynsinger...")
        val slåSammenSøknadsopplysningerPerBarn = innsendingService.slåSammenSøknadsopplysningerPerBarn()
        if (slåSammenSøknadsopplysningerPerBarn.isEmpty()) {
            logger.info("Tomt resultat fra søknadsammenslåing")
        }
        return slåSammenSøknadsopplysningerPerBarn
    }

    @GetMapping("$SØKNAD/{søknadId}/arbeidsgivermelding", produces = [MediaType.APPLICATION_PDF_VALUE])
    @ResponseStatus(OK)
    fun lastNedArbeidsgivermelding(
        @PathVariable søknadId: UUID,
        @RequestParam organisasjonsnummer: String
    ): ResponseEntity<Resource> {
        val resource = ByteArrayResource(innsendingService.hentArbeidsgiverMeldingFil(søknadId, organisasjonsnummer))

        val filnavn = "Bekreftelse_til_arbeidsgiver_$organisasjonsnummer"

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=$filnavn.pdf")
            .contentLength(resource.byteArray.size.toLong())
            .body(resource)
    }
}
