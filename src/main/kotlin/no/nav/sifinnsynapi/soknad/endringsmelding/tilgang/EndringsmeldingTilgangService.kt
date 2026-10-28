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
 * Tilgangskontroll for endringsmelding pleiepenger. Avgjør om innlogget bruker kan bruke endringsmeldingen,
 * og gir årsakene når svaret er nei.
 *
 * Flyt: hent saker → vurder saker → hent arbeidsgivere for oppslagsperioden → vurder arbeidsforhold.
 * Reglene er rene funksjoner. All I/O skjer her.
 *
 * Saker der alle søknadsperiodene slutter før tillatt endringsperiode, regnes som *eldre*. De blokkerer ikke,
 * og telles ikke med når vi ser etter flere saker. Se [tillattEndringsperiode].
 *
 * **Vurder saker** ([vurderSaker]) stopper ved første treff og gir én årsak:
 * - Ingen saker: [IngenTilgangÅrsak.HAR_INGEN_SAK]
 * - Bare eldre saker: [IngenTilgangÅrsak.SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE]
 * - Mer enn én sak som ikke er eldre: [IngenTilgangÅrsak.HAR_MER_ENN_EN_SAK]
 * - Saken har ingen søknadsperioder: [IngenTilgangÅrsak.HAR_INGEN_PERIODER]
 * - Saken ligger etter tillatt endringsperiode: [IngenTilgangÅrsak.SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE]
 *
 * Ved tilgang gir den saken og oppslagsperioden som arbeidsgiverne hentes for. Mangler barnets
 * identitetsnummer i den aktuelle saken, kastes en feil (5xx), fordi saken da ikke kan sendes inn.
 *
 * **Vurder arbeidsforhold** ([vurderArbeidsforhold]) evaluerer alle reglene og samler årsakene i denne
 * rekkefølgen. Første årsak regnes som hovedårsak og logges i eget felt.
 * - Saken har perioder som selvstendig næringsdrivende med normalarbeidstid over null:
 *   [IngenTilgangÅrsak.HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE]
 * - Arbeidsgiver i saken har to ansettelser i samme uke med opphold:
 *   [IngenTilgangÅrsak.EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD]
 * - Arbeidsgiver som ikke er i saken har flere ansettelser i søknadsperiodene:
 *   [IngenTilgangÅrsak.HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER]
 *
 * **Feil** fra oppslag av saker eller AA-reg går videre og gir 5xx. [OppslagsService.hentArbeidsgivere] kaster
 * ved feil og gir aldri tom liste. En tom liste ville gitt tilgang på feil grunnlag, fordi reglene om
 * arbeidsgivere da aldri slår til.
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
                if (årsaker.isEmpty()) TilgangResultat.tilgang() else TilgangResultat.ingenTilgang(årsaker)
            }
        }

        // Feltene blir egne JSON-felt i loggen, slik at vurderingene kan telles per årsak i Grafana og Kibana.
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
