package no.nav.sifinnsynapi.pdf

import no.nav.sifinnsynapi.util.storForbokstav
import org.springframework.stereotype.Service
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

@Service
class ArbeidsgiverMeldingNavNoPDFGenerator : PDFGenerator<PleiepengerArbeidsgiverMelding>() {

    override val templateNavn: String
        get() = "informasjonsbrev-til-arbeidsgiver-nav-no"


    override fun PleiepengerArbeidsgiverMelding.tilMap(): Map<String, Any?> = mapOf(
        "arbeidsgiver_navn" to arbeidsgivernavn?.storForbokstav(),
        "arbeidstaker_navn" to arbeidstakernavn.storForbokstav(),
        "periode" to mapOf(
            "fom" to DATE_FORMATTER.format(søknadsperiode.fraOgMed),
            "tom" to DATE_FORMATTER.format(søknadsperiode.tilOgMed)
        ),
        "tidspunkt" to DATE_TIME_FORMATTER.format(ZonedDateTime.now(UTC))
    )

    override val bilder: Map<String, String>
        get() = mapOf()
}
