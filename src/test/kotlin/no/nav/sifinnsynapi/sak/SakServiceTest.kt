package no.nav.sifinnsynapi.sak

import assertk.assertThat
import assertk.assertions.hasSize
import io.mockk.every
import io.mockk.mockk
import no.nav.k9.innsyn.sak.*
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.Kildesystem
import no.nav.k9.søknad.felles.Versjon
import no.nav.k9.søknad.felles.personopplysninger.Søker
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.type.SøknadId
import no.nav.k9.søknad.ytelse.psb.v1.PleiepengerSyktBarn
import no.nav.sifinnsynapi.dokumentoversikt.DokumentService
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.LegacyInnsynApiService
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.HentBarnForespørsel
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.oppslag.SøkerOppslagRespons
import no.nav.sifinnsynapi.sak.behandling.BehandlingDAO
import no.nav.sifinnsynapi.sak.behandling.BehandlingService
import no.nav.sifinnsynapi.soknad.PsbSøknadDAO
import no.nav.sifinnsynapi.soknad.SøknadService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URL
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*

class SakServiceTest {

    val behandlingService = mockk<BehandlingService>()
    val dokumentService = mockk<DokumentService>()
    val oppslagsService = mockk<OppslagsService>()
    val søknadService = mockk<SøknadService>()
    val omsorgService = mockk<OmsorgService>()
    val legacyInnsynApiService = mockk<LegacyInnsynApiService>()

    val sakService = SakService(
        behandlingService,
        dokumentService,
        oppslagsService,
        omsorgService,
        søknadService,
        legacyInnsynApiService
    )

    private companion object {
        private val annenSøkerAktørId = "00000000000"
        private val hovedSøkerAktørId = "11111111111"
        private val barn1AktørId = "22222222222"
        private val barn2AktørId = "33333333333"
    }

    @BeforeEach
    fun setUp() {
        every { oppslagsService.hentSøker() } returns SøkerOppslagRespons(aktørId = hovedSøkerAktørId)
        every { oppslagsService.systemoppslagBarn(any()) } returns listOf(
            BarnOppslagDTO(
                aktørId = barn1AktørId,
                fødselsdato = LocalDate.parse("2005-02-12"),
                fornavn = "Ole",
                mellomnavn = null,
                etternavn = "Doffen",
                identitetsnummer = "12020567099"
            ),
            BarnOppslagDTO(
                aktørId = barn2AktørId,
                fødselsdato = LocalDate.parse("2005-10-30"),
                fornavn = "Dole",
                mellomnavn = null,
                etternavn = "Doffen",
                identitetsnummer = "30100577255"
            )
        )
    }


    @Test
    fun `skal hente sak og behandling`() {
        val digitalSøknadJP = "journalpostId1"

        every { omsorgService.hentPleietrengendeSøkerHarOmsorgFor(any()) } returns listOf(barn1AktørId)

        every { oppslagsService.systemoppslagBarn(HentBarnForespørsel(identer = listOf(barn1AktørId))) } returns listOf(
            BarnOppslagDTO(
                aktørId = barn1AktørId,
                fødselsdato = LocalDate.parse("2005-02-12"),
                fornavn = "Ole",
                mellomnavn = null,
                etternavn = "Doffen",
                identitetsnummer = "12020567099"
            )
        )

        every { behandlingService.hentBehandlinger(any(), any(), any()) } answers {
            listOf(
                lagBehandlingDAO(
                    setOf(
                        SøknadInfo(SøknadStatus.MOTTATT, digitalSøknadJP, ZonedDateTime.now(), null)
                    )
                )
            ).stream()
        }
        every { søknadService.hentSøknad(any()) } returns lagPsbSøknad(digitalSøknadJP)
        every { dokumentService.hentDokumentOversikt() } returns listOf(lagDokumentDto(digitalSøknadJP))

        val sak = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)

        assertThat(sak).hasSize(1)
        val behandlinger = sak.first().sak.behandlinger
        assertThat(behandlinger).hasSize(1)
    }


    @Test
    fun `ignorerer behandling hvis søknad mangler i oversikt over relevante dokumenter`() {
        val digitalSøknadJP = "journalpostId1"

        every { omsorgService.hentPleietrengendeSøkerHarOmsorgFor(any()) } returns listOf(barn1AktørId)

        every { oppslagsService.systemoppslagBarn(HentBarnForespørsel(identer = listOf(barn1AktørId))) } returns listOf(
            BarnOppslagDTO(
                aktørId = barn1AktørId,
                fødselsdato = LocalDate.parse("2005-02-12"),
                fornavn = "Ole",
                mellomnavn = null,
                etternavn = "Doffen",
                identitetsnummer = "12020567099"
            )
        )

        every { behandlingService.hentBehandlinger(any(), any(), any()) } answers {
            listOf(
                lagBehandlingDAO(
                    setOf(
                        SøknadInfo(SøknadStatus.MOTTATT, digitalSøknadJP, ZonedDateTime.now(), null)
                    )
                )
            ).stream()
        }
        every { søknadService.hentSøknad(any()) } returns lagPsbSøknad(digitalSøknadJP)
        every { dokumentService.hentDokumentOversikt() } returns listOf(lagDokumentDto("randomJP1"))

        val sak = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)

        assertThat(sak).hasSize(1)
        val behandlinger = sak.first().sak.behandlinger
        assertThat(behandlinger).hasSize(0)
    }

    @Test
    fun `ignorerer behandling hvis alle søknader har kildesystem punsj`() {
        val punsjsøknad = "journalpostId1"

        every { omsorgService.hentPleietrengendeSøkerHarOmsorgFor(any()) } returns listOf(barn1AktørId)

        every { oppslagsService.systemoppslagBarn(HentBarnForespørsel(identer = listOf(barn1AktørId))) } returns listOf(
            BarnOppslagDTO(
                aktørId = barn1AktørId,
                fødselsdato = LocalDate.parse("2005-02-12"),
                fornavn = "Ole",
                mellomnavn = null,
                etternavn = "Doffen",
                identitetsnummer = "12020567099"
            )
        )

        every { behandlingService.hentBehandlinger(any(), any(), any()) } answers {
            listOf(
                lagBehandlingDAO(
                    setOf(
                        SøknadInfo(SøknadStatus.MOTTATT, punsjsøknad, ZonedDateTime.now(), Kildesystem.PUNSJ)
                    )
                )
            ).stream()
        }
        every { søknadService.hentSøknad(any()) } returns lagPsbSøknad(punsjsøknad)
        every { dokumentService.hentDokumentOversikt() } returns listOf(lagDokumentDto(punsjsøknad)) //ikke mulig i praksis

        val sak = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)

        assertThat(sak).hasSize(1)
        val behandlinger = sak.first().sak.behandlinger
        assertThat(behandlinger).hasSize(0)
    }


    private fun lagDokumentDto(journalpostId: String) = DokumentDTO(
        journalpostId,
        "dokumentInfo1",
        Saksnummer("ABC123"),
        "Tittel",
        DokumentBrevkode.PLEIEPENGER_SYKT_BARN_SOKNAD,
        filtype = "pdf",
        harTilgang = true,
        journalposttype = Journalposttype.INNGÅENDE,
        relevanteDatoer = emptyList(),
        url = URL("http://localhost:8080/dokument/journalpostId1/dokumentInfo1/ARKIV")
    )

    private fun lagPsbSøknad(journalpostId: String) = PsbSøknadDAO(
        journalpostId = journalpostId,
        søkerAktørId = "sak1234",
        pleietrengendeAktørId = "søknadId",
        søknad = JsonUtils.toString(lagSøknad())
    )

    private fun lagSøknad() = Søknad(
        SøknadId.of(UUID.randomUUID().toString()),
        Versjon.of("1.0.0"),
        ZonedDateTime.now(),
        Søker(NorskIdentitetsnummer.of("22222222222")),
        PleiepengerSyktBarn()
    )

    private fun lagBehandlingDAO(søknadInfos: Set<SøknadInfo>): BehandlingDAO {
        return BehandlingDAO(
            UUID.randomUUID(),
            hovedSøkerAktørId,
            barn1AktørId,
            "sak1234",
            JsonUtils.toString(lagBehandling(søknadInfos)),
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN
            )
        }

    private fun lagBehandling(søknadInfos: Set<SøknadInfo>): Behandling {
        return Behandling(
            UUID.randomUUID(),
            ZonedDateTime.now(),
            ZonedDateTime.now(),
            BehandlingResultat.INNVILGET,
            BehandlingStatus.OPPRETTET,
            søknadInfos,
            setOf(
                Aksjonspunkt(Aksjonspunkt.Venteårsak.INNTEKTSMELDING, ZonedDateTime.now())
            ),
            false,
            Fagsak(
                Saksnummer("ABC123"), AktørId("11111111111"), AktørId("22222222222"), FagsakYtelseType.PLEIEPENGER_SYKT_BARN
            )
        )
    }
}
