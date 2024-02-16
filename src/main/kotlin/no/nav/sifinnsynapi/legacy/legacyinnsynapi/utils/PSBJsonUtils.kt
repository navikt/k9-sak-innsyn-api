package no.nav.sifinnsynapi.legacy.legacyinnsynapi.utils

import no.nav.sifinnsynapi.legacy.legacyinnsynapi.PleiepengesøknadMedOrganisasjonsnummerIkkeFunnetException
import no.nav.sifinnsynapi.oppslag.Organisasjon
import org.json.JSONArray
import org.json.JSONObject

object PSBJsonUtils {
    const val ARBEIDSGIVERE = "arbeidsgivere"
    const val ORGANISASJONER = "organisasjoner"
    const val ORGANISASJONSNUMMER = "organisasjonsnummer"

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
}
