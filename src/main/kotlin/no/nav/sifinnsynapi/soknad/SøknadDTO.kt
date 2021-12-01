package no.nav.sifinnsynapi.soknad

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.k9.søknad.Søknad
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*

data class SøknadDTO @JsonCreator constructor(
    @JsonProperty("pleietrengendeAktørId") val pleietrengendeAktørId: String,
    @JsonProperty("søknad") val søknad: Søknad
) {
    override fun toString(): String {
        return "SøknadDTO()"
    }
}
