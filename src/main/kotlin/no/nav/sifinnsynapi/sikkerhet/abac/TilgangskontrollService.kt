package no.nav.sifinnsynapi.sikkerhet.abac

import no.nav.k9.felles.sikkerhet.abac.ÅrsakIkkeTilgang
import no.nav.sif.abac.kontrakt.abac.BeskyttetRessursActionAttributt
import no.nav.sif.abac.kontrakt.abac.ResourceType
import no.nav.sif.abac.kontrakt.abac.dto.OperasjonDto
import no.nav.sif.abac.kontrakt.abac.dto.PersonerOperasjonDto
import no.nav.sif.abac.kontrakt.person.AktørId
import no.nav.sif.abac.kontrakt.person.PersonIdent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Service
import org.springframework.web.ErrorResponseException

/**
 * Egen tilgangskontroll for saksbehandler, i tillegg til den k9-sak gjør.
 *
 * Pleietrengende sjekkes også, fordi svaret sier noe om sakene deres. Kalleren sender inn pleietrengende
 * fra de samme søknadene som vurderes, så en søknad som lagres underveis ikke slipper unna sjekken.
 * Bare et eksplisitt ja fra sif-abac-pdp gir tilgang. Avslag gir 403, feil gir 5xx.
 */
@Service
class TilgangskontrollService(
    private val sifAbacPdpService: SifAbacPdpService,
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(TilgangskontrollService::class.java)
        private val DRIFT_ACTIONS = setOf(
            BeskyttetRessursActionAttributt.READ,
            BeskyttetRessursActionAttributt.UPDATE,
            BeskyttetRessursActionAttributt.CREATE,
        )
    }

    fun krevLesetilgangTilSøkerOgPleietrengende(søkerAktørId: String, pleietrengendeAktørIder: Collection<String>) {
        krevTilgang(
            aktørIder = listOf(søkerAktørId) + pleietrengendeAktørIder,
            resourceType = ResourceType.FAGSAK,
            action = BeskyttetRessursActionAttributt.READ,
        )
    }

    fun krevDriftstilgangTilAktører(action: BeskyttetRessursActionAttributt, aktørIder: Collection<String>) {
        require(action in DRIFT_ACTIONS) { "Ugyldig action for drift: $action" }
        require(aktørIder.isNotEmpty()) { "Drifttilgang sjekk krever minst én aktør (på sak det gjelder)" }
        krevTilgang(
            aktørIder = aktørIder,
            resourceType = ResourceType.DRIFT,
            action = action,
        )
    }

    /**
     * Som [krevDriftstilgangTilAktører], men med fødselsnummer. Da kan tilgangen sjekkes før vi slår opp aktørId.
     */
    fun krevDriftstilgangTilPersoner(action: BeskyttetRessursActionAttributt, personIdenter: Collection<String>) {
        require(action in DRIFT_ACTIONS) { "Ugyldig action for drift: $action" }
        require(personIdenter.isNotEmpty()) { "Drifttilgang sjekk krever minst én person (på sak det gjelder)" }
        krevTilgang(
            personIdenter = personIdenter,
            resourceType = ResourceType.DRIFT,
            action = action,
        )
    }

    private fun krevTilgang(
        aktørIder: Collection<String> = emptyList(),
        personIdenter: Collection<String> = emptyList(),
        resourceType: ResourceType,
        action: BeskyttetRessursActionAttributt,
    ) {
        val beslutning = sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(
            PersonerOperasjonDto(
                aktørIder.distinct().map { AktørId(it) },
                personIdenter.distinct().map { PersonIdent(it) },
                OperasjonDto(resourceType, action, emptySet()),
            )
        )

        if (!beslutning.harTilgang()) {
            // Årsakene logges ikke, fordi koder som kode 6 og egen ansatt sier noe om personene i saken.
            val tekniskFeil = ÅrsakIkkeTilgang.TEKNISK_FEIL in beslutning.årsakerForIkkeTilgang()
            logger.info("Bruker fikk ikke tilgang til aktør. Ressurs: {}, action: {}, teknisk feil: {}", resourceType, action, tekniskFeil)
            throw ErrorResponseException(
                HttpStatus.FORBIDDEN,
                ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Forespurt tilgang til person på sak er ikke tillatt"),
                null,
            )
        }
    }
}
