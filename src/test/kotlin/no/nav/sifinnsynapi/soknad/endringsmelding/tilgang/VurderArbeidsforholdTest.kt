package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.TilgangTestUtils.ENDRINGSPERIODE
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.TilgangTestUtils.periode
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.TilgangTestUtils.standardSak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Endringsperiode 2024-01-01/2024-12-31 (2024-01-01 er en mandag),
 * kjent orgnr 111, ukjent orgnr 999. Datoer uten år i testnavnene gjelder 2024.
 */
class VurderArbeidsforholdTest {

    private val kjentSak = standardSak(orgnr = setOf(KJENT))

    @Test
    fun `SN-arbeidstid gir avslag`() {
        assertEquals(
            listOf(HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE),
            vurderArbeidsforhold(standardSak(harSN = true), emptyList(), ENDRINGSPERIODE)
        )
    }

    @Test
    fun `opphold samme uke hos kjent arbeidsgiver gir avslag`() {
        assertEquals(
            listOf(EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD),
            vurderArbeidsforhold(kjentSak, listOf(arbeidsgiver(KJENT, "01-01/01-02", "01-04/01-05")), ENDRINGSPERIODE)
        )
    }

    @Test
    fun `ukjent arbeidsgiver med to ansettelser gir avslag`() {
        assertEquals(
            listOf(HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER),
            vurderArbeidsforhold(kjentSak, listOf(arbeidsgiver(UKJENT, "01-01/01-10", "01-15/01-20")), ENDRINGSPERIODE)
        )
    }

    @Test
    fun `alle tre reglene gir alle tre årsakene i fast rekkefølge`() {
        assertEquals(
            listOf(
                HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE,
                EN_ARBEIDSGIVER_TO_ANSETTELSER_SAMME_UKE_MED_OPPHOLD,
                HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER,
            ),
            vurderArbeidsforhold(
                standardSak(orgnr = setOf(KJENT), harSN = true),
                // Ukjent først, for å vise at rekkefølgen ikke avhenger av rekkefølgen fra AA-reg.
                listOf(
                    arbeidsgiver(UKJENT, "01-01/01-10", "01-15/01-20"),
                    arbeidsgiver(KJENT, "01-01/01-02", "01-04/01-05"),
                ),
                ENDRINGSPERIODE
            )
        )
    }

    @Test
    fun `ingen arbeidsgivere og ingen SN gir tilgang`() {
        assertEquals(emptyList<IngenTilgangÅrsak>(), vurderArbeidsforhold(standardSak(), emptyList(), ENDRINGSPERIODE))
    }

    @Test
    fun `opphold over ukeskifte gir tilgang`() {
        assertTilgang(arbeidsgiver(KJENT, "01-01/01-02", "01-10/01-11"))
    }

    @Test
    fun `sammenhengende ansettelser samme uke gir tilgang`() {
        assertTilgang(arbeidsgiver(KJENT, "01-01/01-02", "01-03/01-05"))
    }

    @Test
    fun `opphold dekket av en overlappende periode gir tilgang`() {
        assertTilgang(arbeidsgiver(KJENT, "01-01/01-02", "01-01/01-05", "01-04/01-05"))
    }

    @Test
    fun `én ansettelse hos kjent arbeidsgiver gir tilgang`() {
        assertTilgang(arbeidsgiver(KJENT, "01-01/01-31"))
    }

    @Test
    fun `opphold bare før endringsperioden gir tilgang`() {
        assertTilgang(
            ArbeidsgiverForTilgang(
                KJENT,
                listOf(ansettelse("2019-01-07", "2019-01-08"), ansettelse("2019-01-10", "2024-06-30"))
            )
        )
    }

    @Test
    fun `ukjent arbeidsgiver med én ansettelse gir tilgang`() {
        assertTilgang(arbeidsgiver(UKJENT, "01-01/01-10"))
    }

    @Test
    fun `ukjent arbeidsgiver der bare én ansettelse overlapper søknadsperioden gir tilgang`() {
        assertTilgang(arbeidsgiver(UKJENT, "01-01/01-10", "06-01/06-30"))
    }

    @Test
    fun `ukjent arbeidsgiver med avsluttet og pågående ansettelse som begge overlapper gir avslag`() {
        assertEquals(
            listOf(HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER),
            vurderArbeidsforhold(
                kjentSak,
                listOf(ArbeidsgiverForTilgang(UKJENT, listOf(ansettelse("2023-12-01", "2024-01-10"), ansettelse("2024-01-15", null)))),
                ENDRINGSPERIODE
            )
        )
    }

    @Test
    fun `pågående ansettelse som starter etter endringsperioden ignoreres ved opphold samme uke`() {
        // 31.12.2024 og 02.01.2025 er i samme isouke.
        assertTilgang(
            ArbeidsgiverForTilgang(KJENT, listOf(ansettelse("2024-01-01", "2024-12-31"), ansettelse("2025-01-02", null)))
        )
    }

    @Test
    fun `opphold samme uke sorterer periodene selv`() {
        val perioder = listOf(
            periode("2025-01-20/2025-01-26"),
            periode("2025-01-06/2025-01-12"),
            periode("2025-01-13/2025-01-15"),
            periode("2025-01-17/2025-01-19"),
        )

        // Oppholdet er 16.01, i uke 3.
        assertTrue(perioderSlutterOgStarterSammeUkeMedOpphold(perioder))
    }

    @Test
    fun `opphold samme uke gir false for tom liste og én periode`() {
        assertFalse(perioderSlutterOgStarterSammeUkeMedOpphold(emptyList()))
        assertFalse(perioderSlutterOgStarterSammeUkeMedOpphold(listOf(periode("2025-01-06/2025-01-12"))))
    }

    private fun assertTilgang(arbeidsgiver: ArbeidsgiverForTilgang) =
        assertEquals(emptyList<IngenTilgangÅrsak>(), vurderArbeidsforhold(kjentSak, listOf(arbeidsgiver), ENDRINGSPERIODE))

    private companion object {
        const val KJENT = "111"
        const val UKJENT = "999"

        /** Perioder på formen "MM-dd/MM-dd" i 2024. */
        fun arbeidsgiver(orgnr: String, vararg perioder: String) = ArbeidsgiverForTilgang(
            organisasjonsnummer = orgnr,
            ansettelsesperioder = perioder.map {
                val (fom, tom) = it.split("/")
                ansettelse("2024-$fom", "2024-$tom")
            },
        )

        fun ansettelse(fom: String, tom: String?) =
            Ansettelsesperiode(fom = LocalDate.parse(fom), tom = tom?.let(LocalDate::parse))
    }
}
