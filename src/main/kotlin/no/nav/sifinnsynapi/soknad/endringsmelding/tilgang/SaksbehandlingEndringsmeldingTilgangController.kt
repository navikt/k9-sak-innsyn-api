package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import no.nav.k9.felles.log.audit.Auditdata
import no.nav.k9.felles.log.audit.AuditdataHeader
import no.nav.k9.felles.log.audit.CefField
import no.nav.k9.felles.log.audit.CefFieldName
import no.nav.k9.felles.log.audit.EventClassId
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.RequiredIssuers
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.sifinnsynapi.Routes.SAKSBEHANDLING_PSB_ENDRINGSMELDING_TILGANG
import no.nav.sifinnsynapi.audit.Auditlogger
import no.nav.sifinnsynapi.config.Issuers
import no.nav.sifinnsynapi.sikkerhet.AuthorizationService
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Lar saksbehandler se om søkeren kan bruke endringsmeldingen, med samme svar som søkeren får.
 *
 * Kalles av k9-sak med OBO-token. k9-sak har ansvar for at saksbehandleren har tilgang til søkeren (ABAC).
 * Derfor godtas bare kall fra k9-sak (azp), og oppslaget audit-logges med saksbehandlerens NAVident.
 */
@RestController
@RequiredIssuers(
    ProtectedWithClaims(issuer = Issuers.AZURE, claimMap = ["NAVident=*"])
)
class SaksbehandlingEndringsmeldingTilgangController(
    private val endringsmeldingTilgangService: EndringsmeldingTilgangService,
    private val auditlogger: Auditlogger,
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    private val authorizationService: AuthorizationService,
) {

    @PostMapping(
        SAKSBEHANDLING_PSB_ENDRINGSMELDING_TILGANG,
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun hentTilgang(@Valid @RequestBody forespørsel: SaksbehandlingTilgangForespørsel): ResponseEntity<TilgangResultat> {
        if (!authorizationService.kallErFraK9Sak()) {
            val problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN)
            problemDetail.detail = "Endepunktet kan bare kalles fra k9-sak"
            throw ErrorResponseException(HttpStatus.FORBIDDEN, problemDetail, null)
        }

        val navIdent = tokenValidationContextHolder
            .getTokenValidationContext()
            .firstValidToken
            ?.jwtTokenClaims
            ?.getStringClaim("NAVident") ?: throw IllegalStateException("Mangler NAVident")

        auditLogg(innloggetIdent = navIdent, berørtBrukerIdent = forespørsel.søkerAktørId)

        return ResponseEntity.ok()
            // Utledet av søkerens saker, så det skal ikke mellomlagres.
            .cacheControl(CacheControl.noStore())
            .body(endringsmeldingTilgangService.vurderTilgang(forespørsel.søkerAktørId))
    }

    private fun auditLogg(innloggetIdent: String, berørtBrukerIdent: String) {
        auditlogger.logg(
            Auditdata(
                AuditdataHeader.Builder()
                    .medVendor(auditlogger.vendor)
                    .medProduct(auditlogger.product)
                    .medSeverity("INFO")
                    .medName("Tilgang til endringsmelding for pleiepenger")
                    .medEventClassId(EventClassId.AUDIT_ACCESS)
                    .build(),
                setOf(
                    CefField(CefFieldName.EVENT_TIME, LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC) * 1000L),
                    CefField(CefFieldName.REQUEST, SAKSBEHANDLING_PSB_ENDRINGSMELDING_TILGANG),
                    CefField(CefFieldName.USER_ID, innloggetIdent),
                    CefField(CefFieldName.BERORT_BRUKER_ID, berørtBrukerIdent)
                )
            )
        )
    }
}

data class SaksbehandlingTilgangForespørsel(
    // Ikke ${validatedValue} i meldingen, den havner i feilsvaret og loggen.
    @field:Pattern(regexp = "^\\d{13}$", message = "søkerAktørId må være 13 siffer")
    val søkerAktørId: String,
)
