package no.nav.sifinnsynapi.legacy.legacyinnsynapi.utils

import no.nav.sifinnsynapi.legacy.legacyinnsynapi.PleiepengesøknadMedOrganisasjonsnummerIkkeFunnetException
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.utils.PSBJsonUtils.ARBEIDSGIVERE
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.utils.PSBJsonUtils.ORGANISASJONER
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.utils.PSBJsonUtils.ORGANISASJONSNUMMER
import no.nav.sifinnsynapi.oppslag.Organisasjon
import org.json.JSONArray
import org.json.JSONObject

object PSBJsonUtils {
    const val ARBEIDSGIVERE = "arbeidsgivere"
    const val ORGANISASJONER = "organisasjoner"
    const val ORGANISASJONSNUMMER = "organisasjonsnummer"

    const val SØKNAD_ID = "søknadId"
    const val SØKER = "søker"
    const val SØKER_FORNAVN = "fornavn"
    const val SØKER_MELLOMNAVN = "mellomnavn"
    const val SØKER_ETTERNAVN = "etternavn"
    const val AKTØR_ID = "aktørId"
    const val MOTTATT = "mottatt"
    const val FØDSELSNUMMER = "fødselsnummer"
    const val FRA_OG_MED = "fraOgMed"
    const val TIL_OG_MED = "tilOgMed"
    const val ORGANISASJONSNAVN = "navn"


    fun JSONObject.finnOrganisasjon(søknadId: String, organisasjonsnummer: String): JSONObject {
        val organisasjoner = when (val arbeidsgivereObjekt = get(ARBEIDSGIVERE)) {
            is JSONObject -> arbeidsgivereObjekt.getJSONArray(ORGANISASJONER)
            is JSONArray -> arbeidsgivereObjekt
            else -> throw Error("Ugyldig type for feltet $ARBEIDSGIVERE. Forventet enten JSONObject eller JSONArray, men fikk ${arbeidsgivereObjekt.javaClass}")
        }

        var organisasjon: JSONObject? = null

        for (i in 0 until organisasjoner.length()) {
            val org = organisasjoner.getJSONObject(i)
            if (org.getString(ORGANISASJONSNUMMER) == organisasjonsnummer) {
                organisasjon = org
            }
        }

        if (organisasjon == null) throw PleiepengesøknadMedOrganisasjonsnummerIkkeFunnetException(
            søknadId,
            organisasjonsnummer
        )
        return organisasjon
    }

    fun JSONObject.arbeidsgivere(): MutableList<Organisasjon> {
        val organisasjonerJsonArray = when (val arbeidsgivereObjekt = get(ARBEIDSGIVERE)) {
            is JSONObject -> arbeidsgivereObjekt.getJSONArray(ORGANISASJONER)
            is JSONArray -> arbeidsgivereObjekt
            else -> throw Error("Ugyldig type for feltet $ARBEIDSGIVERE. Forventet enten JSONObject eller JSONArray, men fikk ${arbeidsgivereObjekt.javaClass}")
        }

        val organisasjoner = mutableListOf<Organisasjon>()

        for (i in 0 until organisasjonerJsonArray.length()) {
            val org = organisasjonerJsonArray.getJSONObject(i)
            val organisasjonsnummer = org.getString(ORGANISASJONSNUMMER)
            val organisasjonsnavn = org.optString("navn", null)
            organisasjoner.add(Organisasjon(organisasjonsnummer, organisasjonsnavn))
        }

        return organisasjoner
    }

    fun JSONObject.tilArbeidstakernavn(): String = when (optString(SØKER_MELLOMNAVN, null)) {
        null -> "${getString(SØKER_FORNAVN)} ${getString(SØKER_ETTERNAVN)}"
        else -> "${getString(SØKER_FORNAVN)} ${getString(SØKER_MELLOMNAVN)} ${getString(SØKER_ETTERNAVN)}"
    }
}
