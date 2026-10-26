package no.nav.sifinnsynapi.endringsmelding.psb

import no.nav.sifinnsynapi.endringsmelding.psb.IngenTilgangÅrsak.HAR_INGEN_PERIODER
import no.nav.sifinnsynapi.endringsmelding.psb.IngenTilgangÅrsak.HAR_INGEN_SAK
import no.nav.sifinnsynapi.endringsmelding.psb.IngenTilgangÅrsak.HAR_MER_ENN_EN_SAK
import no.nav.sifinnsynapi.endringsmelding.psb.IngenTilgangÅrsak.SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE
import no.nav.sifinnsynapi.endringsmelding.psb.TilgangTestUtils.ENDRINGSPERIODE
import no.nav.sifinnsynapi.endringsmelding.psb.TilgangTestUtils.periode
import no.nav.sifinnsynapi.endringsmelding.psb.TilgangTestUtils.sak
import no.nav.sifinnsynapi.endringsmelding.psb.TilgangTestUtils.standardSak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Saker med ugyldig format testes ikke her, fordi hele oppslaget feiler (5xx) når en sak ikke kan
 * deserialiseres.
 */
class VurderSakerTest {

    @Test
    fun `ingen saker gir harIngenSak`() {
        assertEquals(SakVurdering.IngenTilgang(HAR_INGEN_SAK), vurderSaker(emptyList(), ENDRINGSPERIODE))
    }

    @Test
    fun `bare eldre saker gir søknadsperioderUtenforTillattEndringsperiode`() {
        assertEquals(
            SakVurdering.IngenTilgang(SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE),
            vurderSaker(listOf(sak("2020-01-01/2020-01-31")), ENDRINGSPERIODE)
        )
    }

    @Test
    fun `flere eldre saker og ingen aktuelle gir søknadsperioderUtenforTillattEndringsperiode`() {
        assertEquals(
            SakVurdering.IngenTilgang(SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE),
            vurderSaker(
                listOf(sak("2020-01-01/2020-01-31"), sak("2023-06-01/2023-12-31")),
                ENDRINGSPERIODE
            )
        )
    }

    @Test
    fun `to aktuelle saker gir harMerEnnEnSak`() {
        assertEquals(
            SakVurdering.IngenTilgang(HAR_MER_ENN_EN_SAK),
            vurderSaker(listOf(standardSak(), standardSak()), ENDRINGSPERIODE)
        )
    }

    @Test
    fun `sak uten søknadsperioder er aktuell og gir harIngenPerioder`() {
        assertEquals(
            SakVurdering.IngenTilgang(HAR_INGEN_PERIODER),
            vurderSaker(listOf(sak()), ENDRINGSPERIODE)
        )
    }

    @Test
    fun `sak uten søknadsperioder telles som aktuell sammen med en annen aktuell sak`() {
        assertEquals(
            SakVurdering.IngenTilgang(HAR_MER_ENN_EN_SAK),
            vurderSaker(listOf(sak(), standardSak()), ENDRINGSPERIODE)
        )
    }

    @Test
    fun `sak etter endringsperioden gir søknadsperioderUtenforTillattEndringsperiode`() {
        assertEquals(
            SakVurdering.IngenTilgang(SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE),
            vurderSaker(listOf(sak("2026-01-01/2026-01-31")), ENDRINGSPERIODE)
        )
    }

    @Test
    fun `én aktuell sak gir tilgang`() {
        val sak = standardSak()
        assertEquals(
            SakVurdering.Tilgang(sak, periode("2024-01-01/2024-01-31")),
            vurderSaker(listOf(sak), ENDRINGSPERIODE)
        )
    }

    @Test
    fun `én aktuell og én eldre sak gir tilgang`() {
        val aktuell = standardSak()
        assertEquals(
            SakVurdering.Tilgang(aktuell, periode("2024-01-01/2024-01-31")),
            vurderSaker(listOf(aktuell, sak("2020-01-01/2020-01-31")), ENDRINGSPERIODE)
        )
    }

    @Test
    fun `oppslagsperioden er samlet søknadsperiode når den ligger innenfor endringsperioden`() {
        val sak = sak("2024-02-01/2024-02-15", "2024-03-01/2024-03-31")
        assertEquals(
            SakVurdering.Tilgang(sak, periode("2024-02-01/2024-03-31")),
            vurderSaker(listOf(sak), ENDRINGSPERIODE)
        )
    }

    @Test
    fun `oppslagsperioden klippes til endringsperioden`() {
        val sak = sak("2023-06-01/2023-12-31", "2025-01-01/2025-06-30")
        assertEquals(
            SakVurdering.Tilgang(sak, periode("2024-01-01/2024-12-31")),
            vurderSaker(listOf(sak), ENDRINGSPERIODE)
        )
    }

    @Test
    fun `sak der bare noen perioder slutter før endringsperioden er ikke eldre`() {
        val sak = sak("2020-01-01/2020-01-31", "2024-01-01/2024-01-31")
        assertEquals(
            SakVurdering.Tilgang(sak, periode("2024-01-01/2024-01-31")),
            vurderSaker(listOf(sak), ENDRINGSPERIODE)
        )
    }
}
