package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.TilgangTestUtils.periode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class EndringsperiodeTest {

    @ParameterizedTest(name = "{0} gir {1}")
    @CsvSource(
        "2026-10-26, 2026-07-01/2027-10-31",
        // Årsskifte bakover: tre måneder før januar er oktober året før.
        "2024-01-15, 2023-10-01/2025-01-31",
        // Første og siste dag i måneden.
        "2024-03-01, 2023-12-01/2025-03-31",
        "2024-03-31, 2023-12-01/2025-03-31",
        // Til februar i skuddår: tom blir 29. februar.
        "2023-02-10, 2022-11-01/2024-02-29",
        // Fra 29. februar: tom blir 28. februar året etter.
        "2024-02-29, 2023-11-01/2025-02-28",
    )
    fun `tillatt endringsperiode`(iDag: String, forventet: String) {
        assertEquals(periode(forventet), tillattEndringsperiode(LocalDate.parse(iDag)))
    }
}
