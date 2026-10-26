package no.nav.sifinnsynapi.endringsmelding.psb

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.RequiredIssuers
import no.nav.sifinnsynapi.Routes.ENDRINGSMELDING_PSB_TILGANG
import no.nav.sifinnsynapi.config.Issuers
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Vurderer om innlogget bruker kan bruke endringsmeldingen for pleiepenger sykt barn.
 *
 * Tar ingen parametere. Saker og arbeidsgivere hentes for brukeren i tokenet, så bruker kan bare
 * få vurdert egne data.
 */
@RestController
@RequiredIssuers(
    ProtectedWithClaims(issuer = Issuers.TOKEN_X, claimMap = ["acr=Level4", "acr=idporten-loa-high"], combineWithOr = true)
)
class EndringsmeldingTilgangController(
    private val endringsmeldingTilgangService: EndringsmeldingTilgangService,
) {

    @GetMapping(ENDRINGSMELDING_PSB_TILGANG, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentTilgang(): ResponseEntity<TilgangResultat> =
        ResponseEntity.ok()
            // Svaret er utledet av brukerens saker og skal ikke mellomlagres i nettleser eller proxy.
            .cacheControl(CacheControl.noStore())
            .body(endringsmeldingTilgangService.vurderTilgang())
}
