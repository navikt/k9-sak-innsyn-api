package no.nav.sifinnsynapi.endringsmelding.psb

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
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
 * Reglene er rene funksjoner og beskrevet der. All I/O skjer her.
 *
 * Feil ved henting av saker eller arbeidsgivere går videre og gir 5xx. En tom arbeidsgiverliste ved feil
 * ville gitt tilgang på feil grunnlag.
 */
@Service
class EndringsmeldingTilgangService(
    private val innsendingService: InnsendingService,
    private val oppslagsService: OppslagsService,
    private val meterRegistry: MeterRegistry,
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
                if (årsaker.isEmpty()) TilgangResultat.tilgang() else TilgangResultat.ingenTilgang(årsaker)
            }
        }

        logger.info(
            "Vurderte tilgang til endringsmelding. kanBruke={}, årsaker={}",
            resultat.kanBruke,
            resultat.årsaker.map { it.kode }
        )
        registrerVurdering(resultat.årsaker.firstOrNull())
        return resultat
    }

    // Private arbeidsgivere inneholder fnr og hentes ikke. Reglene bruker bare organisasjoner.
    private fun hentArbeidsgivere(oppslagsperiode: Datoperiode): List<ArbeidsgiverForTilgang> =
        oppslagsService.hentArbeidsgivere(fraOgMed = oppslagsperiode.fom, tilOgMed = oppslagsperiode.tom)
            .arbeidsgivere.organisasjoner.grupperPerArbeidsgiver()

    /**
     * Teller én gang per vurdering, tagget med første årsak eller "tilgang".
     * Taggen er årsakskoden uten æ, ø og å, slik at den er enkel å bruke i spørringer.
     * Verdiene er faste, så antallet er begrenset og ingenting kan knyttes til en bruker.
     */
    private fun registrerVurdering(førsteÅrsak: IngenTilgangÅrsak?) =
        Counter.builder("endringsmelding_tilgang_vurdering")
            .description("Teller vurderinger av tilgang til endringsmelding pleiepenger, per første årsak")
            .tag("aarsak", førsteÅrsak?.kode?.utenNorskeTegn() ?: "tilgang")
            .register(meterRegistry)
            .increment()

    private fun String.utenNorskeTegn(): String = this
        .replace("æ", "ae").replace("Æ", "Ae")
        .replace("ø", "o").replace("Ø", "O")
        .replace("å", "aa").replace("Å", "Aa")
}
