package no.nav.sifinnsynapi.endringsmelding.psb

import com.fasterxml.jackson.annotation.JsonValue

/**
 * Respons fra GET /endringsmelding/psb/tilgang.
 *
 * [årsaker] er tom når [kanBruke] er true. Første årsak er hovedårsaken, så rekkefølgen er en del av
 * kontrakten.
 */
data class TilgangResultat(
    val kanBruke: Boolean,
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
