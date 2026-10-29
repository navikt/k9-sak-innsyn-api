package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.Datoperiode
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.SakForTilgang
import java.time.LocalDate

/**
 * Felles testdata for tilgangsreglene: endringsperiode 2024-01-01/2024-12-31 (2024-01-01 er en mandag) og standardsak med søknadsperiode januar 2024.
 */
internal object TilgangTestUtils {
    internal val ENDRINGSPERIODE = periode("2024-01-01/2024-12-31")

    /** Periode på formen "yyyy-MM-dd/yyyy-MM-dd". */
    internal fun periode(iso: String): Datoperiode {
        val (fom, tom) = iso.split("/")
        return Datoperiode(LocalDate.parse(fom), LocalDate.parse(tom))
    }

    internal fun sak(
        vararg søknadsperioder: String,
        orgnr: Set<String> = emptySet(),
        harSN: Boolean = false,
        barnHarIdentitetsnummer: Boolean = true,
    ) = SakForTilgang(
        søknadsperioder = søknadsperioder.map { periode(it) },
        arbeidsgiverOrgnr = orgnr,
        harArbeidstidSomSelvstendigNæringsdrivende = harSN,
        barnHarIdentitetsnummer = barnHarIdentitetsnummer,
    )

    internal fun standardSak(orgnr: Set<String> = emptySet(), harSN: Boolean = false) =
        sak("2024-01-01/2024-01-31", orgnr = orgnr, harSN = harSN)
}
