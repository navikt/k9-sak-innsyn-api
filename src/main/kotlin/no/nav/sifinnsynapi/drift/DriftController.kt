package no.nav.sifinnsynapi.drift

import jakarta.validation.Valid
import no.nav.k9.felles.log.audit.*
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.RequiredIssuers
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.sifinnsynapi.Routes.SØKNAD
import no.nav.sifinnsynapi.audit.Auditlogger
import no.nav.sifinnsynapi.config.Issuers
import no.nav.sifinnsynapi.forvaltning.AktørBytteRequest
import no.nav.sifinnsynapi.forvaltning.AktørBytteRespons
import no.nav.sifinnsynapi.oppslag.HentIdenterForespørsel
import no.nav.sifinnsynapi.oppslag.IdentGruppe
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.sikkerhet.AuthorizationService
import no.nav.sifinnsynapi.soknad.DebugDTO
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
    private val oppslagsService: OppslagsService,
    private val authorizationService: AuthorizationService

) {
    companion object {
        val logger = LoggerFactory.getLogger(DriftController::class.java)
    }

    @PostMapping(
        "/debug$SØKNAD",
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(OK)
    fun debugSøknader(@RequestBody debugForespørsel: DebugSøknadForespørsel): List<DebugDTO> {
        val identTilInnloggetBruker: String = tokenValidationContextHolder
            .getTokenValidationContext()
            .firstValidToken
            ?.jwtTokenClaims
            ?.getStringClaim("NAVident") ?: throw IllegalStateException("Mangler NAVident")

        val søkerAktørId = hentAktørId(debugForespørsel.søkerNorskIdentitetsnummer)
        val pleietrengendeAktørIder = debugForespørsel.pleietrengendeNorskIdentitetsnummer.map { hentAktørId(it) }

        auditLogg(
            uri = "/debug$SØKNAD",
            innloggetIdent = identTilInnloggetBruker,
            berørtBrukerIdent = søkerAktørId,
            eventClassId = EventClassId.AUDIT_ACCESS
        )

        pleietrengendeAktørIder.forEach {
            auditLogg(
                uri = "/debug$SØKNAD",
                innloggetIdent = identTilInnloggetBruker,
                berørtBrukerIdent = it,
                eventClassId = EventClassId.AUDIT_SEARCH
            )
        }
        return driftService.slåSammenSøknadsopplysningerPerBarn(
            søkerAktørId,
            pleietrengendeAktørIder,
            debugForespørsel.ekskluderteSøknadIder
        )
    }


    @PostMapping(
        "/forvaltning/oppdaterAktoerId",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ProtectedWithClaims(issuer = Issuers.AZURE, claimMap = ["NAVident=*"])
    fun oppdaterAktoerId(@Valid @RequestBody aktørBytteRequest: AktørBytteRequest): ResponseEntity<AktørBytteRespons> {
        if (!authorizationService.harTilgangTilDriftRolle()) {
            val problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN)
            problemDetail.detail = "Mangler driftsrolle"
            throw ErrorResponseException(HttpStatus.FORBIDDEN, problemDetail, null)
        }

        val identTilInnloggetBruker: String = tokenValidationContextHolder
            .getTokenValidationContext()
            .firstValidToken
            ?.jwtTokenClaims
            ?.getStringClaim("NAVident") ?: throw IllegalStateException("Mangler NAVident")

        val antallOppdaterte = driftService.oppdaterAktørId(
            aktørBytteRequest.gyldigAktør,
            aktørBytteRequest.utgåttAktør
        )

        if (antallOppdaterte > 0) {
            auditLogg(
                uri = "/forvaltning/oppdaterAktoerId",
                innloggetIdent = identTilInnloggetBruker,
                berørtBrukerIdent = aktørBytteRequest.gyldigAktør,
                eventClassId = EventClassId.AUDIT_SEARCH
            )

        }

        return ResponseEntity.ok(AktørBytteRespons(antallOppdaterte))
    }

    fun hentAktørId(norskIdentitetsnummer: String): String {
        return oppslagsService.hentIdenter(
            HentIdenterForespørsel(
                identer = listOf(norskIdentitetsnummer),
                identGrupper = listOf(IdentGruppe.AKTORID)
            )
        ).first().identer.first().ident
    }

    private fun auditLogg(uri: String, innloggetIdent: String, berørtBrukerIdent: String, eventClassId: EventClassId) {
        auditlogger.logg(
            Auditdata(
                AuditdataHeader.Builder()
                    .medVendor(auditlogger.vendor)
                    .medProduct(auditlogger.product)
                    .medSeverity("INFO")
                    .medName("Debug sammenslåtte søknadsopplysninger")
                    .medEventClassId(eventClassId)
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

data class DebugSøknadForespørsel(
    val søkerNorskIdentitetsnummer: String,
    val pleietrengendeNorskIdentitetsnummer: List<String>,
    val ekskluderteSøknadIder: List<String> = emptyList()
)
