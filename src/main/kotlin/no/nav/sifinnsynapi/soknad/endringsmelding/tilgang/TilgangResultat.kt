package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Respons fra GET /psb/endringsmelding/tilgang.
 *
 * [årsaker] er tom når [kanBruke] er true. Første årsak er hovedårsaken, så rekkefølgen er en del av
 * kontrakten.
 */
data class TilgangResultat(
    // Uten eget eksempel lager springdoc kanBruke=true sammen med en årsak.
    @field:Schema(example = "false")
    val kanBruke: Boolean,
    // Uten eksplisitt navn utleder springdoc «getårsaker» fra getteren getÅrsaker().
    @get:JsonProperty("årsaker")
    val årsaker: List<IngenTilgangÅrsak>,
) {
    companion object {
        fun tilgang() = TilgangResultat(kanBruke = true, årsaker = emptyList())
        fun ingenTilgang(årsaker: List<IngenTilgangÅrsak>): TilgangResultat {
            require(årsaker.isNotEmpty()) { "Avslag uten årsak." }
            return TilgangResultat(kanBruke = false, årsaker = årsaker)
        }
    }
}

/**
 * [kode] er verdien i JSON-responsen og en del av kontrakten. Konstantnavnene er ikke det.
 */
enum class IngenTilgangÅrsak(@get:JsonValue val kode: String) {
    HAR_INGEN_SAK("harIngenSak"),
    SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE("søknadsperioderUtenforTillattEndringsperiode"),
    HAR_MER_ENN_EN_SAK("harMerEnnEnSak"),
    HAR_INGEN_PERIODER("harIngenPerioder"),
    HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE("harArbeidstidSomSelvstendigNæringsdrivende"),
    EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD("enArbeidsgiverToAnsettelserSammeUkeMedOpphold"),
    HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER("harFlereAnsettelsesforholdHosUkjentArbeidsgiver"),
}
