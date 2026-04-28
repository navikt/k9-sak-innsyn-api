package no.nav.sifinnsynapi.util

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.k9.søknad.JsonUtilsJackson2

object K9Jackson2ObjectMapper {
    val objectMapper: ObjectMapper = JsonUtilsJackson2.getObjectMapper()

    fun <T> fromString(s: String, clazz: Class<T>): T = objectMapper.readValue(s, clazz)

    fun toString(obj: Any): String = JsonUtilsJackson2.toString(obj, objectMapper)
}

