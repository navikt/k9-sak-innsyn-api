package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import net.logstash.logback.argument.StructuredArguments.entries
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.soknad.InnsendingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

/**
 * Avgjør om innlogget bruker kan bruke endringsmeldingen for pleiepenger, og gir årsakene når svaret er nei.
 *
 * Flyt: hent saker → [vurderSaker] → hent arbeidsgivere for oppslagsperioden → [vurderArbeidsforhold].
 *
 * Feil fra oppslagene gir 5xx, aldri tilgang. Se [OppslagsService.hentArbeidsgivere].
 */
@Service
class EndringsmeldingTilgangService(
    private val innsendingService: InnsendingService,
    private val oppslagsService: OppslagsService,
    private val clock: Clock,
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(EndringsmeldingTilgangService::class.java)
    }

    fun vurderTilgang(): TilgangResultat {
        val endringsperiode = tillattEndringsperiode(LocalDate.now(clock))
        val saker = innsendingService.slåSammenSøknadsopplysningerPerBarn().map { it.tilSakForTilgang() }

        val resultat = when (val sakVurdering = vurderSaker(saker, endringsperiode)) {
            is SakVurdering.IngenTilgang -> TilgangResultat.ingenTilgang(listOf(sakVurdering.årsak))
            is SakVurdering.Tilgang -> {
                val arbeidsgivere = hentArbeidsgivere(sakVurdering.oppslagsperiode)
                val årsaker = vurderArbeidsforhold(sakVurdering.sak, arbeidsgivere, endringsperiode)

                if (årsaker.isEmpty())
                    TilgangResultat.tilgang(endringsperiode)
                else
                    TilgangResultat.ingenTilgang(årsaker)
            }
        }

        // Logg resultat og bruk første årsak hvis !kanBruke
        val felter = buildMap<String, Any> {
            put("hendelse", "endringsmelding_tilgang_vurdert")
            put("kan_bruke", resultat.kanBruke)
            resultat.årsaker.firstOrNull()?.let { put("forste_aarsak", it.kode) }
        }
        logger.info("Vurderte tilgang til endringsmelding. {}", entries(felter))
        return resultat
    }

    private fun hentArbeidsgivere(oppslagsperiode: Datoperiode): List<ArbeidsgiverForTilgang> =
        oppslagsService.hentArbeidsgivere(fraOgMed = oppslagsperiode.fom, tilOgMed = oppslagsperiode.tom)
            .grupperPerArbeidsgiver()
}
