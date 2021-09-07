package no.nav.sifinnsynapi.common

import com.fasterxml.jackson.annotation.JsonValue
import java.util.*
import javax.persistence.Embeddable

@Embeddable
data class AktørId(@get:JsonValue var aktørId: String? = null) {

    override fun hashCode(): Int {
        return Objects.hashCode(aktørId)
    }

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
        val obj = other as AktørId
        if (aktørId == null) {
            if (obj.aktørId != null) {
                return false
            }
        } else if (aktørId != obj.aktørId) {
            return false
        }
        return true
    }

    override fun toString(): String {
        return javaClass.simpleName + "[aktørId=******]"
    }

    companion object {
        fun valueOf(aktørId: String?): AktørId {
            val id = AktørId()
            id.aktørId = aktørId
            return id
        }
    }
}
