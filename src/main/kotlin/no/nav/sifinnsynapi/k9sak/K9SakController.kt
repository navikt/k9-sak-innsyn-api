package no.nav.sifinnsynapi.k9sak

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.RequiredIssuers
import no.nav.sifinnsynapi.Routes
import no.nav.sifinnsynapi.common.AktørId
import no.nav.sifinnsynapi.config.Issuers
import no.nav.sifinnsynapi.oppslag.OppslagsService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController


@RestController
@RequiredIssuers(
    ProtectedWithClaims(issuer = Issuers.TOKEN_X, claimMap = ["acr=Level4"])
)
class K9SakController(
    private val k9SakService: K9SakService,
    private val oppslagsService: OppslagsService
) {
    @PostMapping(Routes.K9SAK_OMSORGSDAGER_KRONISK_SYKT_BARN_GYLDIG_VEDTAK, produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    fun hentSisteGyldigeVedtakForAktorId(@RequestBody requestDto: OmsorgsdagerKronsinskSuktBarnRequestDto ): HentSisteGyldigeVedtakForAktorIdResponse? {
        val aktørId = oppslagsService.hentSøker()?.aktørId?: throw IllegalStateException("Fant ikke aktørId for innlogget bruker")

        return k9SakService.hentSisteGyldigeVedtakForAktorId(HentSisteGyldigeVedtakForAktorIdDto(
            aktørId = AktørId(aktørId),
            pleietrengendeAktørId = requestDto.pleietrengendeAktørId
        ))
    }

    data class OmsorgsdagerKronsinskSuktBarnRequestDto(
        val pleietrengendeAktørId: AktørId
    )
}
