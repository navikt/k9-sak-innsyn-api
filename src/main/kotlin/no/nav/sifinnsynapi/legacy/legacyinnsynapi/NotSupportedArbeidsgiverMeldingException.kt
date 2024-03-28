package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import no.nav.sifinnsynapi.util.ServletUtils
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset

class NotSupportedArbeidsgiverMeldingException(søknadId: String, søknadstype: LegacySøknadstype) :
    ErrorResponseException(HttpStatus.BAD_REQUEST, asProblemDetail(søknadId, søknadstype), null) {
    private companion object {
        private fun asProblemDetail(søknadId: String, søknadstype: LegacySøknadstype): ProblemDetail {
            val problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST)
            problemDetail.title = "Arbeidsgivermelding ikke støttet"
            problemDetail.detail =
                "Søknad med søknadId = $søknadId  og søknadstype = $søknadstype støtter ikke arbeidsgivermelding."
            problemDetail.type = URI("/problem-details/arbeidsgivermelding-ikke-støttet")
            ServletUtils.currentHttpRequest()?.let {
                problemDetail.instance = URI(URLDecoder.decode(it.requestURL.toString(), Charset.defaultCharset()))
            }
            return problemDetail
        }
    }
}
