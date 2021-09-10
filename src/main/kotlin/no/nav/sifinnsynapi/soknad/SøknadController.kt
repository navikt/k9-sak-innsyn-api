package no.nav.sifinnsynapi.soknad

import no.nav.security.token.support.core.api.Protected
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.sifinnsynapi.Routes.SØKNAD
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@ProtectedWithClaims(issuer = "tokenx")
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

    @GetMapping("$SØKNAD/testdata", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Protected
    @ResponseStatus(OK)
    fun hentTestSøknader(): List<SøknadDTO> {
        return søknadService.hentTestData()
    }
}
