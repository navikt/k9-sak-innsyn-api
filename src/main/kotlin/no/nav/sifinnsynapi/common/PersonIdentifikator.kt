package no.nav.sifinnsynapi.common

import com.fasterxml.jackson.annotation.JsonValue
import com.google.common.base.Objects
import com.google.common.base.Strings
import jakarta.persistence.Embeddable

@Embeddable
data class PersonIdentifikator(
        @get:JsonValue var personIdent: String? = null) {

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
        val obj = other as PersonIdentifikator
        if (personIdent == null) {
            if (obj.personIdent != null) {
                return false
            }
        } else if (personIdent != obj.personIdent) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        return Objects.hashCode(personIdent)
    }

    override fun toString(): String {
        return javaClass.simpleName + " [personIdent=" + mask(personIdent) + "]"
    }

    companion object {
        fun valueOf(fnr: String?): PersonIdentifikator {
            val id = PersonIdentifikator()
            id.personIdent = fnr
            return id
        }

        fun mask(value: String?): String? {
            return if (value != null && value.length == 11) Strings.padEnd(value.substring(0, 6), 11, '*') else value
        }
    }
}
