package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_INGEN_PERIODER
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_INGEN_SAK
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_MER_ENN_EN_SAK
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.TilgangTestUtils.ENDRINGSPERIODE
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.TilgangTestUtils.periode
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.TilgangTestUtils.sak
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.TilgangTestUtils.standardSak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VurderSakerTest {

    @Test
    fun `ingen saker gir harIngenSak`() {
        assertEquals(SakVurdering.IngenTilgang(HAR_INGEN_SAK), vurderSaker(emptyList(), ENDRINGSPERIODE))
    }

    @Test
    fun `bare eldre saker gir søknadsperioderUtenforTillattEndringsperiode`() {
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
    fun `sak etter endringsperioden gir søknadsperioderUtenforTillattEndringsperiode`() {
        assertEquals(
            SakVurdering.IngenTilgang(SØKNADSPERIODER_UTENFOR_TILLATT_ENDRINGSPERIODE),
            vurderSaker(listOf(sak("2026-01-01/2026-01-31")), ENDRINGSPERIODE)
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

    @Test
    fun `aktuell sak uten barnets identitetsnummer gir feil uten personopplysninger`() {
        val sak = sak("2024-01-01/2024-01-31", barnHarIdentitetsnummer = false)

        val feil = assertThrows<IllegalStateException> { vurderSaker(listOf(sak), ENDRINGSPERIODE) }

        assertEquals("Barnet i saken mangler identitetsnummer.", feil.message)
    }

    @Test
    fun `eldre sak uten barnets identitetsnummer påvirker ikke vurderingen`() {
        val aktuell = standardSak()
        assertEquals(
            SakVurdering.Tilgang(aktuell, periode("2024-01-01/2024-01-31")),
            vurderSaker(
                listOf(aktuell, sak("2020-01-01/2020-01-31", barnHarIdentitetsnummer = false)),
                ENDRINGSPERIODE
            )
        )
    }

    @Test
    fun `flere aktuelle saker gir harMerEnnEnSak selv om en mangler barnets identitetsnummer`() {
        assertEquals(
            SakVurdering.IngenTilgang(HAR_MER_ENN_EN_SAK),
            vurderSaker(
                listOf(standardSak(), sak("2024-01-01/2024-01-31", barnHarIdentitetsnummer = false)),
                ENDRINGSPERIODE
            )
        )
    }
}
