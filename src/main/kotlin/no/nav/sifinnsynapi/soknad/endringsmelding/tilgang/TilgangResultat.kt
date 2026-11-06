package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Respons fra GET /psb/endringsmelding/tilgang.
 *
 * [årsaker] er tom når [kanBruke] er true. Første årsak er hovedårsaken, så rekkefølgen er en del av
 * kontrakten.
 * [tillattEndringsperiode] er perioden som kan endres, og settes kun når [kanBruke] er true.
 */
data class TilgangResultat(
    // Uten eget eksempel lager springdoc kanBruke=true sammen med en årsak.
    @field:Schema(example = "false")
    val kanBruke: Boolean,
    // Uten eksplisitt navn utleder springdoc «getårsaker» fra getteren getÅrsaker().
    @get:JsonProperty("årsaker")
    val årsaker: List<IngenTilgangÅrsak>,
    // Denne settes kun hvis kanBruke=true
    @field:Schema(nullable = true, description = "Satt bare når kanBruke er true.")
    val tillattEndringsperiode: Datoperiode? = null,
) {
    companion object {
        fun tilgang(tillattEndringsperiode: Datoperiode) = TilgangResultat(
            kanBruke = true,
            årsaker = emptyList(),
            tillattEndringsperiode = tillattEndringsperiode
        )
        fun ingenTilgang(årsaker: List<IngenTilgangÅrsak>): TilgangResultat {
            require(årsaker.isNotEmpty()) { "Avslag uten årsak." }
            return TilgangResultat(kanBruke = false, årsaker = årsaker)
        }
    }
}

/**
 * [saksbehandlerTekst] vises for saksbehandleren i k9-sak-web. Søkeren får egne tekster i endringsmeldingen.
 */
enum class IngenTilgangÅrsak(@get:JsonValue val kode: String, val saksbehandlerTekst: String) {
    HAR_INGEN_SAK("harIngenSak", "Nei, søkeren har ingen sak"),
    SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE(
        "søknadsperioderUtenforTillattEndringsperiode",
        "Nei, søknadsperiodene er utenfor perioden som kan endres",
    ),
    HAR_MER_ENN_EN_SAK("harMerEnnEnSak", "Nei, søkeren har mer enn én sak"),
    HAR_INGEN_PERIODER("harIngenPerioder", "Nei, søkeren har ingen søknadsperioder"),
    BARN_MANGLER_IDENTITETSNUMMER("barnManglerIdentitetsnummer", "Nei, barnet mangler identitetsnummer"),
    HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE(
        "harArbeidstidSomSelvstendigNæringsdrivende",
        "Nei, søkeren har arbeidstid som selvstendig næringsdrivende",
    ),
    EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD(
        "enArbeidsgiverToAnsettelserSammeUkeMedOpphold",
        "Nei, to ansettelser hos samme arbeidsgiver i samme uke, med opphold",
    ),
    HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER(
        "harFlereAnsettelsesforholdHosUkjentArbeidsgiver",
        "Nei, flere ansettelsesforhold hos ukjent arbeidsgiver",
    ),
}
