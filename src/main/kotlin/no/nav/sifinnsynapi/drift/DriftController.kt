package no.nav.sifinnsynapi.drift

import no.nav.k9.felles.log.audit.Auditdata
import no.nav.k9.felles.log.audit.AuditdataHeader
import no.nav.k9.felles.log.audit.CefField
import no.nav.k9.felles.log.audit.CefFieldName
import no.nav.k9.felles.log.audit.EventClassId
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.RequiredIssuers
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.sifinnsynapi.Routes.SØKNAD
import no.nav.sifinnsynapi.audit.Auditlogger
import no.nav.sifinnsynapi.config.Issuers
import no.nav.sifinnsynapi.oppslag.HentIdenter
import no.nav.sifinnsynapi.oppslag.IdentGruppe
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.soknad.DebugDTO
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.ZoneOffset

@RestController
@RequiredIssuers(
    ProtectedWithClaims(issuer = Issuers.AZURE)
)
class DriftController(
    private val driftService: DriftService,
    private val auditlogger: Auditlogger,
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    private val oppslagsService: OppslagsService
) {
    companion object {
        val logger = LoggerFactory.getLogger(DriftController::class.java)
    }

    @GetMapping("/debug$SØKNAD", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(OK)
    fun debugSøknader(
        @RequestParam søkerNorskIdentitetsnummer: String,
        @RequestParam pleietrengendeNorskIdentitetsnummer: List<String>,
    ): List<DebugDTO> {
        val identTilInnloggetBruker: String = tokenValidationContextHolder.tokenValidationContext.firstValidToken.get().jwtTokenClaims.getStringClaim("NAVident")

        val søkerAktørId = oppslagsService.hentIdenter(
            HentIdenter(
                identer = listOf(søkerNorskIdentitetsnummer),
                identGrupper = listOf(IdentGruppe.AKTORID)
            )).first().identer.first()

        val pleietrengendeAktørIder = oppslagsService.hentIdenter(
            HentIdenter(
                identer = pleietrengendeNorskIdentitetsnummer,
                identGrupper = listOf(IdentGruppe.AKTORID)
            )).flatMap { it.identer }

        auditLogg(uri = "/debug$SØKNAD", innloggetIdent = identTilInnloggetBruker, berørtBrukerIdent = søkerAktørId)
        return driftService.slåSammenSøknadsopplysningerPerBarn(søkerAktørId, pleietrengendeAktørIder)
    }

    private fun auditLogg(uri: String, innloggetIdent: String, berørtBrukerIdent: String) {
        auditlogger.logg(
            Auditdata(
                AuditdataHeader.Builder()
                    .medVendor(auditlogger.vendor)
                    .medProduct(auditlogger.product)
                    .medSeverity("INFO")
                    .medName("Debug sammenslåtte søknadsopplysninger")
                    .medEventClassId(EventClassId.AUDIT_ACCESS)
                    .build(),
                setOf(
                    CefField(CefFieldName.EVENT_TIME, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) * 1000L),
                    CefField(CefFieldName.REQUEST, uri),
                    CefField(CefFieldName.USER_ID, innloggetIdent),
                    CefField(CefFieldName.BERORT_BRUKER_ID, berørtBrukerIdent)
                )
            )
        )
    }
}
