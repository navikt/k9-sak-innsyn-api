package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import no.nav.sifinnsynapi.util.ServletUtils
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset

class LegacySøknadNotFoundException(søknadId: String) :
    ErrorResponseException(HttpStatus.NOT_FOUND, asProblemDetail(søknadId), null) {
    private companion object {
        private fun asProblemDetail(søknadId: String): ProblemDetail {
            val problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
            problemDetail.title = "Søknad ble ikke funnet"
            problemDetail.detail = "Søknad med id $søknadId ble ikke funnet."
            problemDetail.type = URI("/problem-details/søknad-ikke-funnet")
            ServletUtils.currentHttpRequest()?.let {
                problemDetail.instance = URI(URLDecoder.decode(it.requestURL.toString(), Charset.defaultCharset()))
            }
            return problemDetail
        }
    }
}
