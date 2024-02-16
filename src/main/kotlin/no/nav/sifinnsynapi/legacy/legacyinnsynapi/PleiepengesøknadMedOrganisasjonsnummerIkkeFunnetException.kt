package no.nav.sifinnsynapi.legacy.legacyinnsynapi

import no.nav.sifinnsynapi.util.ServletUtils
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset

class PleiepengesøknadMedOrganisasjonsnummerIkkeFunnetException(søknadId: String, organisasjonsnummer: String) :
    ErrorResponseException(HttpStatus.NOT_FOUND, asProblemDetail(søknadId, organisasjonsnummer), null) {
    private companion object {
        private fun asProblemDetail(søknadId: String, organisasjonsnummer: String): ProblemDetail {
            val problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
            problemDetail.title = "Arbeidsgiver ikke funnet"
            problemDetail.detail =
                "Søknad med søknadId = $søknadId  og organisasjonsnummer = $organisasjonsnummer ble ikke funnet."
            problemDetail.type = URI("/problem-details/arbeidsgiver-ikke-funnet")
            ServletUtils.currentHttpRequest()?.let {
                problemDetail.instance = URI(URLDecoder.decode(it.requestURL.toString(), Charset.defaultCharset()))
            }

            return problemDetail
        }
    }
}
