package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.oppslag.OrganisasjonOppslagDTO
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.TilgangTestUtils.periode
import no.nav.sifinnsynapi.soknad.InnsendingService
import no.nav.sifinnsynapi.soknad.SøknadDTO
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER
import no.nav.sifinnsynapi.soknad.endringsmelding.tilgang.IngenTilgangÅrsak.HAR_INGEN_SAK
import no.nav.sifinnsynapi.utils.defaultSøknad
import no.nav.k9.søknad.felles.type.Periode
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class EndringsmeldingTilgangServiceTest {

    private companion object {
        val OSLO_ZONE_ID: ZoneId = ZoneId.of("Europe/Oslo")
    }

    private val innsendingService = mockk<InnsendingService>()
    private val oppslagsService = mockk<OppslagsService>()

    // I dag = 2024-03-15 gir endringsperiode 2023-12-01/2025-03-31.
    private val clock = Clock.fixed(
        ZonedDateTime.of(2024, 3, 15, 12, 0, 0, 0, OSLO_ZONE_ID).toInstant(),
        OSLO_ZONE_ID
    )
    private val endringsperiode = periode("2023-12-01/2025-03-31")

    private val service = EndringsmeldingTilgangService(
        innsendingService, oppslagsService, clock
    )

    @BeforeEach
    fun setUp() {
        every { oppslagsService.hentArbeidsgivere(any(), any()) } returns
            arbeidsgivere()
    }

    @Test
    fun `avslag på sakene henter ikke arbeidsgivere`() {
        every { innsendingService.slåSammenSøknadsopplysningerPerBarn() } returns emptyList()

        assertEquals(TilgangResultat.ingenTilgang(listOf(HAR_INGEN_SAK)), service.vurderTilgang())
        verify(exactly = 0) { oppslagsService.hentArbeidsgivere(any(), any()) }
    }

    @Test
    fun `tilgang henter arbeidsgivere for oppslagsperioden`() {
        every { innsendingService.slåSammenSøknadsopplysningerPerBarn() } returns
            listOf(innsynSøknad("2023-06-01/2024-01-31"))

        assertEquals(TilgangResultat.tilgang(endringsperiode), service.vurderTilgang())
        verify {
            oppslagsService.hentArbeidsgivere(
                fraOgMed = LocalDate.parse("2023-12-01"),
                tilOgMed = LocalDate.parse("2024-01-31"),
            )
        }
    }

    @Test
    fun `avslag på arbeidsforhold bruker arbeidsgiverne fra AA-reg gruppert per orgnr`() {
        every { innsendingService.slåSammenSøknadsopplysningerPerBarn() } returns
            listOf(innsynSøknad("2024-01-01/2024-01-31"))
        every { oppslagsService.hentArbeidsgivere(any(), any()) } returns
            arbeidsgivere(
                OrganisasjonOppslagDTO("999", LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-10")),
                OrganisasjonOppslagDTO("999", LocalDate.parse("2024-01-15"), LocalDate.parse("2024-01-20")),
            )

        assertEquals(
            TilgangResultat.ingenTilgang(listOf(HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER)),
            service.vurderTilgang()
        )
    }

    @Test
    fun `feil fra AA-reg gir exception og ikke tilgang`() {
        every { innsendingService.slåSammenSøknadsopplysningerPerBarn() } returns
            listOf(innsynSøknad("2024-01-01/2024-01-31"))
        every { oppslagsService.hentArbeidsgivere(any(), any()) } throws
            IllegalStateException("Feil ved henting av arbeidsgivere")

        assertThrows<IllegalStateException> { service.vurderTilgang() }
    }

    private fun innsynSøknad(søknadsperiode: String) = SøknadDTO(
        barn = BarnOppslagDTO(
            fødselsdato = LocalDate.parse("2020-01-01"),
            fornavn = "BARN",
            etternavn = "BARNESEN",
            aktørId = "1000000000001",
            identitetsnummer = "02119970079",
        ),
        søknad = defaultSøknad(søknadsPeriode = Periode(søknadsperiode), arbeidstid = Arbeidstid()),
    )

    private fun arbeidsgivere(vararg organisasjoner: OrganisasjonOppslagDTO) = organisasjoner.toList()
}
