package no.nav.sifinnsynapi.sak.inntektsmelding

import no.nav.k9.innsyn.sak.Saksnummer
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.RequiredIssuers
import no.nav.sifinnsynapi.config.Issuers
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController("/sak")
@RequiredIssuers(
    ProtectedWithClaims(issuer = Issuers.TOKEN_X, claimMap = ["acr=Level4"])
)
class SakInntektsmeldingerController(private val inntektsmeldingService: InntektsmeldingService) {

    @GetMapping("{saksnummer}/inntektsmeldinger")
    @Transactional(readOnly = true)
    fun hentInntektsmeldingerPåSak(@PathVariable saksnummer: String): List<SakInntektsmeldingDTO?> {
        return inntektsmeldingService.hentInntektsmeldingerPåSak(saksnummer = Saksnummer(saksnummer))
    }
}
