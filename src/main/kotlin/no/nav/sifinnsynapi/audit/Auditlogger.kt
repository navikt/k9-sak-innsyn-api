package no.nav.sifinnsynapi.audit

import no.nav.k9.felles.log.audit.Auditdata
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Auditlogging til Arcsight i Common Event Format (CEF). Dette er en erstatning for sporingslog.
 *
 *
 * Metode for å inkludere dette i prosjektet:
 *
 *  1. Legg inn påkrevde parameteere (se konstruktur under).
 *  1. Undertrykk gammel sporingslogg i logback.xml med `<logger level="OFF" name="sporing" additivity="false" /> `
 *  1. Sett opp "auditLogger" som beskrevet her: https://github.com/navikt/naudit}
 *  1. Ta kontakt med Arcsight-gruppen for at de skal motta/endre format for loggen som kommer via "audit.nais".
 *
 */
@Component
class Auditlogger(
    private val auditConfiguration: AuditConfiguration,
) {

    val vendor: String get() = auditConfiguration.auditVendor()
    val product: String get() = auditConfiguration.auditProduct()


    fun logg(auditdata: Auditdata) {
        auditLogger.info(auditdata.toString())
    }

    private companion object {
        private val auditLogger = LoggerFactory.getLogger("auditLogger")
    }
}
