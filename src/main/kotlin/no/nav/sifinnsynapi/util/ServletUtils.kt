package no.nav.sifinnsynapi.util

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

object ServletUtils {

    fun currentHttpRequest(): HttpServletRequest? {
        val requestAttributes = RequestContextHolder.getRequestAttributes()
        return if (requestAttributes is ServletRequestAttributes) {
            requestAttributes.request
        } else null
    }
}
