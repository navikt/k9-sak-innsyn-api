package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.utils.PSBJsonUtils.arbeidsgivere
import no.nav.sifinnsynapi.oppslag.Organisasjon
import org.json.JSONObject
import java.util.*

data class LegacySøknadDTO @JsonCreator constructor(
    @JsonProperty("søknadId") val søknadId: UUID,
    @JsonProperty("søknadstype") val søknadstype: LegacySøknadstype,
    @JsonProperty("søknad") val søknad: Map<String, Any>,
    @JsonProperty("saksId") val saksId: String?,
    @JsonProperty("journalpostId") val journalpostId: String
) {
    override fun toString(): String {
        return "SøknadDTO()"
    }

    fun arbeidsgivere(): List<Organisasjon> = when (søknadstype) {
        LegacySøknadstype.PP_SYKT_BARN -> JSONObject(søknad).arbeidsgivere()
        else -> throw NotSupportedArbeidsgiverMeldingException(søknadId.toString(), søknadstype)
    }
}

enum class LegacySøknadstype {
    PP_SYKT_BARN,
    PP_ETTERSENDELSE,
    PP_LIVETS_SLUTTFASE_ETTERSENDELSE,
    OMS_ETTERSENDELSE,
    PP_SYKT_BARN_ENDRINGSMELDING;

    fun gjelderPP() = when (this) {
        PP_SYKT_BARN, PP_ETTERSENDELSE, PP_SYKT_BARN_ENDRINGSMELDING -> true
        else -> false
    }
}

