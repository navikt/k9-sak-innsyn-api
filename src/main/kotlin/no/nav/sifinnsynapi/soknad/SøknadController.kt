package no.nav.sifinnsynapi.soknad

import no.nav.security.token.support.core.api.Protected
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.sifinnsynapi.Routes.SØKNAD
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@ProtectedWithClaims(issuer = "tokenx", claimMap = ["acr=Level4"])
class SøknadController(
        private val søknadService: SøknadService
) {
    companion object {
        val logger = LoggerFactory.getLogger(SøknadController::class.java)
    }

    @GetMapping(SØKNAD, produces = [MediaType.APPLICATION_JSON_VALUE])
    @Protected
    @ResponseStatus(OK)
    fun hentSøknader(): List<SøknadDTO> {
        logger.info("Forsøker å hente søknader...")
        val søknader = søknadService.hentSøknader()
        logger.info("Fant {} søknader", søknader.size)
        return søknader
    }

    @GetMapping("$SØKNAD/{søknadId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Protected
    @ResponseStatus(OK)
    fun hentSøknad(@PathVariable søknadId: UUID): SøknadDTO {
        logger.info("Forsøker å hente søknad med id : {}...", søknadId)
        return søknadService.hentSøknad(søknadId)
    }
}
