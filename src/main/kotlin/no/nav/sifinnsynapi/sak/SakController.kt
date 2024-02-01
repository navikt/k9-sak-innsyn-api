package no.nav.sifinnsynapi.sak

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.RequiredIssuers
import no.nav.security.token.support.core.api.Unprotected
import no.nav.sifinnsynapi.Routes
import no.nav.sifinnsynapi.config.Issuers
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController


@RestController
@RequiredIssuers(
    ProtectedWithClaims(issuer = Issuers.TOKEN_X, claimMap = ["acr=Level4"])
)
class SakController(
    private val sakService: SakService
) {
    @GetMapping(Routes.SAKER, produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    fun hentSaker(): List<SakDTO> {
        return sakService.hentSaker()
    }

    @GetMapping("${Routes.SAKER}/saksbehandlingstid", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    @Unprotected
    fun hentSaksbehandlingstid(): SaksbehandlingtidDTO {
        return sakService.hentGenerellSaksbehandlingstid()
    }
}
