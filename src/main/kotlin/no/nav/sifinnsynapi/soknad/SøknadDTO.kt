package no.nav.sifinnsynapi.soknad

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.k9.søknad.Søknad
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO

data class SøknadDTO @JsonCreator constructor(
    @JsonProperty("barn") val barn: BarnOppslagDTO,
    @JsonProperty("søknad") val søknad: Søknad,
    @JsonProperty("søknader") val søknader: List<Søknad>? = null
) {
    override fun toString(): String {
        return "SøknadDTO()"
    }
}

data class DebugDTO @JsonCreator constructor(
    @JsonProperty("pleietrengendeAktørId") val pleietrengendeAktørId: String,
    @JsonProperty("søknad") val søknad: Søknad,
    @JsonProperty("søknader") val søknader: List<Søknad>? = null
) {
    override fun toString(): String {
        return "SøknadDTO()"
    }
}


