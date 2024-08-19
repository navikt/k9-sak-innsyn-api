package no.nav.sifinnsynapi.forvaltning

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.jetbrains.annotations.NotNull

data class AktørBytteRespons(
    @JsonProperty
    @NotNull
    val antallOppdaterteRader: Int,

)
