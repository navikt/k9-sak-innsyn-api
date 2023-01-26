package no.nav.sifinnsynapi.soknad

import no.nav.security.token.support.core.api.Protected
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.RequiredIssuers
import no.nav.security.token.support.core.api.Unprotected
import no.nav.sifinnsynapi.Routes.SØKNAD
import no.nav.sifinnsynapi.config.Issuers
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequiredIssuers(
    ProtectedWithClaims(issuer = Issuers.ID_PORTEN, claimMap = ["acr=Level4"]),
    ProtectedWithClaims(issuer = Issuers.TOKEN_X, claimMap = ["acr=Level4"])
)
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
        logger.info("Forsøker å hente søknadsopplynsinger...")
        return søknadService.slåSammenSøknadsopplysningerPerBarn()
    }

    @GetMapping("/debug$SØKNAD", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Unprotected
    @ResponseStatus(OK)
    fun debugSøknader(@RequestParam søkerAktørId: String, @RequestParam pleietrengendeAktørIder: List<String>): List<SøknadDTO> {
        logger.info("Forsøker å hente søknadsopplynsinger...")
        return søknadService.slåSammenSøknadsopplysningerPerBarn(søkerAktørId, pleietrengendeAktørIder)
    }
}
