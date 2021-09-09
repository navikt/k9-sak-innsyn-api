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
        @JsonProperty("søknadId") val søknadId: UUID,
        @JsonProperty("søknad") val søknad: Søknad,
        @JsonProperty("opprettet") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC") val opprettet: ZonedDateTime? = null,
        @JsonProperty("endret") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss") val endret: LocalDateTime? = null,
        @JsonProperty("behandlingsdato") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss") val behandlingsdato: LocalDate? = null
) {
    override fun toString(): String {
        return "SøknadDTO(søknadId=$søknadId)"
    }
}
