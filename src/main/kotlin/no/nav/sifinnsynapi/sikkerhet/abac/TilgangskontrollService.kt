package no.nav.sifinnsynapi.sikkerhet.abac

import no.nav.sif.abac.kontrakt.abac.BeskyttetRessursActionAttributt
import no.nav.sif.abac.kontrakt.abac.ResourceType
import no.nav.sif.abac.kontrakt.abac.dto.OperasjonDto
import no.nav.sif.abac.kontrakt.abac.dto.PersonerOperasjonDto
import no.nav.sif.abac.kontrakt.person.AktørId
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
    }

    fun krevLesetilgangTilSøkerOgPleietrengende(søkerAktørId: String, pleietrengendeAktørIder: Collection<String>) {
        val aktørIder = (listOf(søkerAktørId) + pleietrengendeAktørIder)
            .distinct()
            .map { AktørId(it) }

        val beslutning = sifAbacPdpService.sjekkTilgangTilPersonerForInnloggetBruker(
            PersonerOperasjonDto(
                aktørIder,
                emptyList(),
                OperasjonDto(ResourceType.FAGSAK, BeskyttetRessursActionAttributt.READ, emptySet()),
            )
        )

        if (!beslutning.harTilgang()) {
            // Årsakene er koder uten personopplysninger. De sendes ikke til k9-sak.
            logger.info("Saksbehandler fikk ikke tilgang til søker. Årsaker: {}", beslutning.årsakerForIkkeTilgang())
            throw ErrorResponseException(
                HttpStatus.FORBIDDEN,
                ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Saksbehandler har ikke tilgang til søkeren"),
                null,
            )
        }
    }
}
