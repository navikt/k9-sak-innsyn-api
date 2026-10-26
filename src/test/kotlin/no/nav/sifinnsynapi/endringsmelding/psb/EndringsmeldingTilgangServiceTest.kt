package no.nav.sifinnsynapi.endringsmelding.psb

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.k9.søknad.felles.type.Periode
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstid
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidInfo
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidPeriodeInfo
import no.nav.sifinnsynapi.endringsmelding.psb.IngenTilgangÅrsak.HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE
import no.nav.sifinnsynapi.endringsmelding.psb.IngenTilgangÅrsak.HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER
import no.nav.sifinnsynapi.endringsmelding.psb.IngenTilgangÅrsak.HAR_INGEN_SAK
import no.nav.sifinnsynapi.oppslag.ArbeidsgivereOppslagDTO
import no.nav.sifinnsynapi.oppslag.ArbeidsgivereOppslagResponsDTO
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.oppslag.OrganisasjonOppslagDTO
import no.nav.sifinnsynapi.soknad.InnsendingService
import no.nav.sifinnsynapi.soknad.SøknadDTO
import no.nav.sifinnsynapi.utils.defaultSøknad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class EndringsmeldingTilgangServiceTest {

    private val innsendingService = mockk<InnsendingService>()
    private val oppslagsService = mockk<OppslagsService>()
    private val meterRegistry = SimpleMeterRegistry()

    // I dag = 2024-03-15 gir endringsperiode 2023-12-01/2025-03-31.
    private val oslo = ZoneId.of("Europe/Oslo")
    private val clock = Clock.fixed(ZonedDateTime.of(2024, 3, 15, 12, 0, 0, 0, oslo).toInstant(), oslo)

    private val service = EndringsmeldingTilgangService(innsendingService, oppslagsService, meterRegistry, clock)

    @BeforeEach
    fun setUp() {
        every { oppslagsService.hentArbeidsgivere(any(), any()) } returns arbeidsgivere()
    }

    @Test
    fun `ingen saker gir avslag og henter ikke arbeidsgivere`() {
        // Også når saker er filtrert bort, f.eks. barn med adressebeskyttelse, blir resultatet tomt.
        every { innsendingService.slåSammenSøknadsopplysningerPerBarn() } returns emptyList()

        assertEquals(TilgangResultat.ingenTilgang(listOf(HAR_INGEN_SAK)), service.vurderTilgang())
        verify(exactly = 0) { oppslagsService.hentArbeidsgivere(any(), any()) }
        assertEquals(1.0, teller("harIngenSak"))
    }

    @Test
    fun `tilgang henter arbeidsgivere for oppslagsperioden`() {
        every { innsendingService.slåSammenSøknadsopplysningerPerBarn() } returns
            listOf(søknadDTO("2023-06-01/2024-01-31"))

        assertEquals(TilgangResultat.tilgang(), service.vurderTilgang())
        verify {
            oppslagsService.hentArbeidsgivere(
                fraOgMed = LocalDate.parse("2023-12-01"),
                tilOgMed = LocalDate.parse("2024-01-31"),
            )
        }
        assertEquals(1.0, teller("tilgang"))
    }

    @Test
    fun `avslag på arbeidsforhold bruker arbeidsgiverne fra AA-reg gruppert per orgnr`() {
        every { innsendingService.slåSammenSøknadsopplysningerPerBarn() } returns
            listOf(søknadDTO("2024-01-01/2024-01-31"))
        every { oppslagsService.hentArbeidsgivere(any(), any()) } returns
            arbeidsgivere(
                OrganisasjonOppslagDTO("888", LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-10")),
                OrganisasjonOppslagDTO("888", LocalDate.parse("2024-01-15"), LocalDate.parse("2024-01-20")),
            )

        assertEquals(
            TilgangResultat.ingenTilgang(listOf(HAR_FLERE_ANSETTELSESFORHOLD_HOS_UKJENT_ARBEIDSGIVER)),
            service.vurderTilgang()
        )
    }

    @Test
    fun `selvstendig næringsdrivende får ikke tilgang`() {
        val arbeidstid = Arbeidstid().medSelvstendigNæringsdrivendeArbeidstidInfo(
            ArbeidstidInfo().medPerioder(
                mapOf(
                    Periode("2024-01-01/2024-01-31") to ArbeidstidPeriodeInfo()
                        .medJobberNormaltTimerPerDag(Duration.ofMinutes(450))
                        .medFaktiskArbeidTimerPerDag(Duration.ZERO)
                )
            )
        )
        every { innsendingService.slåSammenSøknadsopplysningerPerBarn() } returns
            listOf(søknadDTO("2024-01-01/2024-01-31", arbeidstid))

        assertEquals(
            TilgangResultat.ingenTilgang(listOf(HAR_ARBEIDSTID_SOM_SELVSTENDIG_NÆRINGSDRIVENDE)),
            service.vurderTilgang()
        )
        assertEquals(1.0, teller("harArbeidstidSomSelvstendigNaeringsdrivende"))
    }

    @Test
    fun `feil fra AA-reg gir exception og ikke tilgang`() {
        every { innsendingService.slåSammenSøknadsopplysningerPerBarn() } returns
            listOf(søknadDTO("2024-01-01/2024-01-31"))
        every { oppslagsService.hentArbeidsgivere(any(), any()) } throws
            IllegalStateException("Feil ved henting av arbeidsgivere")

        assertThrows<IllegalStateException> { service.vurderTilgang() }
        assertNull(meterRegistry.find("endringsmelding_tilgang_vurdering").counter())
    }

    private fun teller(aarsak: String): Double? =
        meterRegistry.find("endringsmelding_tilgang_vurdering").tag("aarsak", aarsak).counter()?.count()

    private fun søknadDTO(søknadsperiode: String, arbeidstid: Arbeidstid = Arbeidstid()) = SøknadDTO(
        barn = BarnOppslagDTO(
            fødselsdato = LocalDate.parse("2020-01-01"),
            fornavn = "BARN",
            etternavn = "BARNESEN",
            aktørId = "1000000000001",
        ),
        søknad = defaultSøknad(søknadsPeriode = Periode(søknadsperiode), arbeidstid = arbeidstid),
    )

    private fun arbeidsgivere(vararg organisasjoner: OrganisasjonOppslagDTO) =
        ArbeidsgivereOppslagResponsDTO(ArbeidsgivereOppslagDTO(organisasjoner.toList()))
}
