package no.nav.sifinnsynapi.soknad

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.k9.søknad.Søknad

data class SøknadDTO @JsonCreator constructor(
    @JsonProperty("pleietrengendeIdent") val pleietrengendeIdent: String,
    @JsonProperty("søknad") val søknad: Søknad
) {
    override fun toString(): String {
        return "SøknadDTO()"
    }
}


