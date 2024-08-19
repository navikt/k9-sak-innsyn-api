package no.nav.sifinnsynapi.forvaltning

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.jetbrains.annotations.NotNull

data class AktørBytteRequest(
    @JsonProperty
    @NotNull
    @Size(max = 20)
    @Pattern(regexp = "^\\d+$", message = "AktørId [\${validatedValue}] matcher ikke tillatt pattern [{regexp}]")
    val utgåttAktør: String,
    @JsonProperty
    @NotNull
    @Size(max = 20)
    @Pattern(regexp = "^\\d+$", message = "AktørId [\${validatedValue}] matcher ikke tillatt pattern [{regexp}]")
    val gyldigAktør: String
)
