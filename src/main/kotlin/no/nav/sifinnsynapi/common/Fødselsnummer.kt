package no.nav.sifinnsynapi.common

import com.fasterxml.jackson.annotation.JsonValue
import com.google.common.base.Objects
import com.google.common.base.Strings
import javax.persistence.Embeddable

@Embeddable
data class Fødselsnummer(
        @get:JsonValue var fødselsnummer: String? = null) {

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }
        if (javaClass != other.javaClass) {
            return false
        }
        val obj = other as Fødselsnummer
        if (fødselsnummer == null) {
            if (obj.fødselsnummer != null) {
                return false
            }
        } else if (fødselsnummer != obj.fødselsnummer) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        return Objects.hashCode(fødselsnummer)
    }

    override fun toString(): String {
        return javaClass.simpleName + " [fnr=" + mask(fødselsnummer) + "]"
    }

    companion object {
        fun valueOf(fnr: String?): Fødselsnummer {
            val id = Fødselsnummer()
            id.fødselsnummer = fnr
            return id
        }

        fun mask(value: String?): String? {
            return if (value != null && value.length == 11) Strings.padEnd(value.substring(0, 6), 11, '*') else value
        }
    }
}
