package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.RequiredIssuers
import no.nav.sifinnsynapi.Routes.PSB_ENDRINGSMELDING_TILGANG
import no.nav.sifinnsynapi.config.Issuers
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Tar ingen parametere, så bruker kan bare få vurdert egne data. */
@RestController
@RequiredIssuers(
    ProtectedWithClaims(issuer = Issuers.TOKEN_X, claimMap = ["acr=Level4", "acr=idporten-loa-high"], combineWithOr = true)
)
class EndringsmeldingTilgangController(
    private val endringsmeldingTilgangService: EndringsmeldingTilgangService,
) {

    @GetMapping(PSB_ENDRINGSMELDING_TILGANG, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentTilgang(): ResponseEntity<TilgangResultat> =
        ResponseEntity.ok()
            // Utledet av brukerens saker, så det skal ikke mellomlagres.
            .cacheControl(CacheControl.noStore())
            .body(endringsmeldingTilgangService.vurderTilgang())
}
