package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
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
}

enum class LegacySøknadstype {
    PP_SYKT_BARN,
    PP_ETTERSENDELSE,
    PP_LIVETS_SLUTTFASE_ETTERSENDELSE,
    OMS_ETTERSENDELSE,
    PP_SYKT_BARN_ENDRINGSMELDING;

    fun gjelderPP() = when(this){
        PP_SYKT_BARN, PP_ETTERSENDELSE, PP_SYKT_BARN_ENDRINGSMELDING -> true
        else -> false
    }
}

