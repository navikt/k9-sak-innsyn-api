package no.nav.sifinnsynapi.sak

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import no.nav.k9.innsyn.sak.FagsakYtelseType
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.RequiredIssuers
import no.nav.security.token.support.core.api.Unprotected
import no.nav.sifinnsynapi.Routes
import no.nav.sifinnsynapi.config.Issuers
import no.nav.sifinnsynapi.config.SwaggerConfiguration
import no.nav.sifinnsynapi.config.SwaggerConfiguration.Companion.SAKER_RESPONSE_EKSEMPEL
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
    private val sakService: SakService,
) {
    @GetMapping(Routes.SAKER_METADATA, produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    @Operation(
        summary = "Henter metadata om sakene registrert på pleietrengende som bruker har omsorgen for",
        responses = [
            ApiResponse(
                responseCode = "200", description = "OK",
                content = [
                    Content(
                        schema = Schema(implementation = SakerMetadataDTO::class),
                        examples = [
                            ExampleObject(
                                name = "Saker metadata",
                                value = SwaggerConfiguration.SAKER_METADATA_RESPONSE_EKSEMPEL
                            )]
                    )]
            )
        ]
    )
    fun hentSakerMetadata(): List<SakerMetadataDTO> {
        return sakService.hentSakerMetadata(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
    }

    @GetMapping(Routes.SAKER, produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    @Operation(
        summary = "Henter sakene registrert på pleietrengende som bruker har omsorgen for",
        responses = [
            ApiResponse(
                responseCode = "200", description = "OK",
                content = [
                    Content(
                        schema = Schema(implementation = PleietrengendeMedSak::class),
                        examples = [
                            ExampleObject(
                                name = "Pleietrengende med sak",
                                value = SAKER_RESPONSE_EKSEMPEL
                            )]
                    )]
            )
        ]
    )
    fun hentMineSaker(): List<PleietrengendeMedSak> {
        return sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
    }

    @GetMapping("${Routes.SAKER}/saksbehandlingstid", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    @Unprotected
    fun hentSaksbehandlingstid(): SaksbehandlingtidDTO {
        return sakService.hentGenerellSaksbehandlingstid()
    }
}
