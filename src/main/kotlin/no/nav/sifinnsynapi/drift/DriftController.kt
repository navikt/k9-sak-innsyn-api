package no.nav.sifinnsynapi.drift

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.RequiredIssuers
import no.nav.sifinnsynapi.Routes.SØKNAD
import no.nav.sifinnsynapi.audit.AuditLoggerUtils
import no.nav.sifinnsynapi.config.Issuers
import no.nav.sifinnsynapi.soknad.DebugDTO
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequiredIssuers(
    ProtectedWithClaims(issuer = Issuers.AZURE)
)
class DriftController(
    private val driftService: DriftService
) {
    companion object {
        val logger = LoggerFactory.getLogger(DriftController::class.java)
    }

    @GetMapping("/debug$SØKNAD", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(OK)
    fun debugSøknader(@RequestParam søkerAktørId: String, @RequestParam pleietrengendeAktørIder: List<String>): List<DebugDTO> {
        logger.info("Forsøker å hente søknadsopplynsinger...")
        AuditLoggerUtils.auditLogger.info("Bruker henter søknadsopplysninger")
        return driftService.slåSammenSøknadsopplysningerPerBarn(søkerAktørId, pleietrengendeAktørIder)
    }
}
