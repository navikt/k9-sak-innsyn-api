package no.nav.sifinnsynapi.pdf

import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate

class ArbeidsgiverMeldingNavNoPDFGeneratorTest {

    @Test
    fun pdf() {
        val pdf = ArbeidsgiverMeldingNavNoPDFGenerator().genererPDF(
            melding = PleiepengerArbeidsgiverMelding(
                arbeidstakernavn = "Ola Nordmann",
                arbeidsgivernavn = "Sjokkerende Elektriker",
                søknadsperiode = SøknadsPeriode(
                    fraOgMed = LocalDate.now().minusWeeks(1),
                    tilOgMed = LocalDate.now().plusWeeks(1)
                )
            )
        )
        File(pdfPath("Bekreftelse til arbeidsgiver")).writeBytes(pdf)
    }

    private fun pdfPath(filnavn: String) = "${System.getProperty("user.dir")}/generated-pdf-$filnavn.pdf"
}
