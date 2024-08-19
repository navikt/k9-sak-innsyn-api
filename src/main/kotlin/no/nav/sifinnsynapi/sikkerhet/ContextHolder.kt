package no.nav.sifinnsynapi.sikkerhet

import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.spring.SpringTokenValidationContextHolder
import no.nav.sifinnsynapi.config.Issuers
import org.springframework.web.context.request.RequestContextHolder

class ContextHolder private constructor(private val context: SpringTokenValidationContextHolder) {

    companion object {
        private var instans: ContextHolder? = null
        val INSTANCE: ContextHolder
            get() {
                if (instans == null) {
                    instans = ContextHolder(SpringTokenValidationContextHolder())
                }
                return instans!!
            }

    }

    fun requestKontekst(): RequestKontekst? {
        if (RequestContextHolder.getRequestAttributes() == null)
            return null

        val tokenContext = context.getTokenValidationContext()
        val reqIssuerShortNames = tokenContext.issuers //alle issuers på alle validerte tokens i context
        if (reqIssuerShortNames.contains(Issuers.AZURE)) {
            val jwtToken: JwtToken? = tokenContext.getJwtToken(Issuers.AZURE)
            return jwtToken?.let { RequestKontekst(it, Issuers.AZURE) }
        }
        return null
    }

    @JvmRecord
    data class RequestKontekst(val jwtToken: JwtToken, val issuerShortname: String)


}
