package no.nav.sifinnsynapi.sak

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import no.nav.k9.ettersendelse.Ettersendelse
import no.nav.k9.ettersendelse.EttersendelseType
import no.nav.k9.ettersendelse.Pleietrengende
import no.nav.k9.ettersendelse.Ytelse
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
import no.nav.sifinnsynapi.soknad.EttersendelseDAO
import no.nav.sifinnsynapi.soknad.PsbSøknadDAO
import no.nav.sifinnsynapi.soknad.InnsendingService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class SakServiceTest {

    val behandlingService = mockk<BehandlingService>()
    val dokumentService = mockk<DokumentService>()
    val oppslagsService = mockk<OppslagsService>()
    val innsendingService = mockk<InnsendingService>()
    val omsorgService = mockk<OmsorgService>()
    val legacyInnsynApiService = mockk<LegacyInnsynApiService>()

    val sakService = SakService(
        behandlingService,
        dokumentService,
        oppslagsService,
        omsorgService,
        innsendingService,
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

        every { legacyInnsynApiService.hentLegacySøknader(any()) } returns listOf()
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

        every { behandlingService.hentBehandlinger(any(), any()) } answers {
            listOf(
                lagBehandlingDAO(
                    setOf(
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            digitalSøknadJP,
                            ZonedDateTime.now(),
                            null,
                            InnsendingType.SØKNAD
                        )
                    ),
                    ZonedDateTime.now(),
                    BehandlingStatus.OPPRETTET
                )
            ).stream()
        }
        every { innsendingService.hentSøknad(any()) } returns lagPsbSøknad(digitalSøknadJP)
        every { dokumentService.hentDokumentOversikt() } returns listOf(lagDokumentDto(digitalSøknadJP))

        val sak = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)

        assertThat(sak).hasSize(1)
        val behandlinger = sak.first().sak.behandlinger
        assertThat(behandlinger).hasSize(1)
    }


    @Test
    fun `ignorerer behandling hvis søknad mangler i oversikt over relevante dokumenter`() {
        val digitalSøknadJP = "journalpostId1"
        val digitalSøknadJPMedTilhørendeDokument = "journalpostId2"

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

        every { behandlingService.hentBehandlinger(any(), any()) } answers {
            listOf(
                lagBehandlingDAO(
                    setOf(
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            digitalSøknadJP,
                            ZonedDateTime.now(),
                            null,
                            InnsendingType.SØKNAD
                        ),
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            digitalSøknadJPMedTilhørendeDokument,
                            ZonedDateTime.now(),
                            null,
                            InnsendingType.SØKNAD
                        )
                    ),
                    ZonedDateTime.now(),
                    BehandlingStatus.OPPRETTET
                )
            ).stream()
        }
        every { innsendingService.hentSøknad(any()) } returns lagPsbSøknad(digitalSøknadJP)
        every { dokumentService.hentDokumentOversikt() } returns listOf(
            lagDokumentDto("randomJP1"),
            lagDokumentDto(digitalSøknadJPMedTilhørendeDokument)
        )

        val sak = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)

        assertThat(sak).hasSize(1)
        val behandlinger = sak.first().sak.behandlinger
        assertThat(behandlinger).hasSize(1)
    }

    @Test
    fun `Behandling skal ignorere søknader har kildesystem punsj`() {
        val punsjsøknad = "journalpostId1"
        val digitalSøknad = "journalpostId2"

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

        every { behandlingService.hentBehandlinger(any(), any()) } answers {
            listOf(
                lagBehandlingDAO(
                    setOf(
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            punsjsøknad,
                            ZonedDateTime.now(),
                            Kildesystem.PUNSJ,
                            InnsendingType.SØKNAD
                        )
                    ),
                    ZonedDateTime.now(),
                    BehandlingStatus.OPPRETTET
                ),
                lagBehandlingDAO(
                    setOf(
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            digitalSøknad,
                            ZonedDateTime.now(),
                            Kildesystem.SØKNADSDIALOG,
                            InnsendingType.SØKNAD
                        )
                    ),
                    ZonedDateTime.now(),
                    BehandlingStatus.OPPRETTET
                )
            ).stream()
        }
        every { innsendingService.hentSøknad(any()) } returns lagPsbSøknad(punsjsøknad)
        every { innsendingService.hentSøknad(any()) } returns lagPsbSøknad(digitalSøknad)
        every { dokumentService.hentDokumentOversikt() } returns listOf(
            lagDokumentDto(punsjsøknad),
            lagDokumentDto(digitalSøknad)
        ) //ikke mulig i praksis

        val sak = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)

        // Hvis saken ikke inneholder noen behandlinger, så skal den ikke vises.
        assertThat(sak).hasSize(1)
        val behandlinger = sak.first().sak.behandlinger
        assertThat(behandlinger).hasSize(1)
    }

    @Test
    fun `Behandling skal ignorere dersom det kun finnes punsjet søknad og ettersendelse`() {
        val punsjsøknad = "journalpostId1"
        val digitalSøknad = "journalpostId3"
        val digitalEttersendelse = "journalpostId2"

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

        every { behandlingService.hentBehandlinger(any(), any()) } answers {
            listOf(
                lagBehandlingDAO(
                    setOf(
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            punsjsøknad,
                            ZonedDateTime.now(),
                            Kildesystem.PUNSJ,
                            InnsendingType.SØKNAD
                        ),
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            digitalEttersendelse,
                            ZonedDateTime.now(),
                            Kildesystem.SØKNADSDIALOG,
                            InnsendingType.ETTERSENDELSE
                        )
                    ),
                    ZonedDateTime.now(),
                    BehandlingStatus.OPPRETTET
                )
            ).stream()
        }
        every { innsendingService.hentSøknad(any()) } returns lagPsbSøknad(punsjsøknad)
        every { innsendingService.hentSøknad(any()) } returns lagPsbSøknad(digitalSøknad)
        every { innsendingService.hentEttersendelse(any()) } returns lagEttersendelse(digitalEttersendelse)
        every { dokumentService.hentDokumentOversikt() } returns listOf(
            lagDokumentDto(digitalSøknad),
            lagDokumentDto(digitalEttersendelse)
        )

        val sak = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)

        // Hvis saken ikke inneholder noen behandlinger, så skal den ikke vises.
        assertThat(sak).hasSize(0)
    }

    @Test
    fun `Behandling skal ikke ignoreres dersom det finnes punsjet søknad og, digtal søknad og ettersendelse`() {
        val punsjsøknad = "journalpostId1"
        val digitalSøknad = "journalpostId3"
        val digitalEttersendelse = "journalpostId2"

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

        every { behandlingService.hentBehandlinger(any(), any()) } answers {
            listOf(
                lagBehandlingDAO(
                    setOf(
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            punsjsøknad,
                            ZonedDateTime.now(),
                            Kildesystem.PUNSJ,
                            InnsendingType.SØKNAD
                        ),
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            digitalSøknad,
                            ZonedDateTime.now(),
                            Kildesystem.SØKNADSDIALOG,
                            InnsendingType.SØKNAD
                        ),
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            digitalEttersendelse,
                            ZonedDateTime.now(),
                            Kildesystem.SØKNADSDIALOG,
                            InnsendingType.ETTERSENDELSE
                        )
                    ),
                    ZonedDateTime.now(),
                    BehandlingStatus.OPPRETTET
                )
            ).stream()
        }
        every { innsendingService.hentSøknad(any()) } returns lagPsbSøknad(punsjsøknad)
        every { innsendingService.hentSøknad(any()) } returns lagPsbSøknad(digitalSøknad)
        every { innsendingService.hentEttersendelse(any()) } returns lagEttersendelse(digitalEttersendelse)
        every { dokumentService.hentDokumentOversikt() } returns listOf(
            lagDokumentDto(digitalSøknad),
            lagDokumentDto(digitalEttersendelse)
        )

        val sak = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        assertThat(sak).hasSize(1)
    }

    @Test
    fun `Behandling skal ignoreres dersom det er punsjet søknad uten kildesystem og har kun ettersendelse`() {
        val punsjsøknad = "journalpostId1"
        val digitalEttersendelse = "journalpostId2"

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

        every { behandlingService.hentBehandlinger(any(), any()) } answers {
            listOf(
                lagBehandlingDAO(
                    setOf(
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            punsjsøknad,
                            ZonedDateTime.now(),
                            null,
                            InnsendingType.SØKNAD
                        ),
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            digitalEttersendelse,
                            ZonedDateTime.now(),
                            Kildesystem.SØKNADSDIALOG,
                            InnsendingType.ETTERSENDELSE
                        )
                    ),
                    ZonedDateTime.now(),
                    BehandlingStatus.OPPRETTET
                )
            ).stream()
        }
        every { innsendingService.hentSøknad(any()) } returns lagPsbSøknad(punsjsøknad)
        every { innsendingService.hentEttersendelse(any()) } returns lagEttersendelse(digitalEttersendelse)
        every { dokumentService.hentDokumentOversikt() } returns listOf(
            lagDokumentDto(digitalEttersendelse)
        )

        val sak = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)

        // Hvis saken ikke inneholder noen behandlinger, så skal den ikke vises.
        assertThat(sak).hasSize(0)
    }

    @Test
    fun `Skal ikke vise noen sak hvis alle behandlinger er filtrert ut`() {
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

        every { behandlingService.hentBehandlinger(any(), any()) } answers {
            listOf(
                lagBehandlingDAO(
                    setOf(
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            punsjsøknad,
                            ZonedDateTime.now(),
                            Kildesystem.PUNSJ,
                            InnsendingType.SØKNAD
                        )
                    ),
                    ZonedDateTime.now(),
                    BehandlingStatus.OPPRETTET
                )
            ).stream()
        }
        every { innsendingService.hentSøknad(any()) } returns lagPsbSøknad(punsjsøknad)
        every { dokumentService.hentDokumentOversikt() } returns listOf(lagDokumentDto(punsjsøknad)) //ikke mulig i praksis

        val sak = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)

        // Hvis saken ikke inneholder noen behandlinger, så skal den ikke vises.
        assertThat(sak).hasSize(0)
    }

    @Test
    fun `Saken skal vises som avsluttet hvis siste behandling kun inneholder ettersendelse`() {
        val søknad1JournalpostId = "journalpostId1"
        val søknad2JournalpostId = "journalpostId2"
        val ettersendelseJournalpostId = "journalpostId3"

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

        every { behandlingService.hentBehandlinger(any(), any()) } answers {
            listOf(
                lagBehandlingDAO(
                    opprettetTidspunkt = ZonedDateTime.now().minusHours(1),
                    behandlingStatus = BehandlingStatus.AVSLUTTET,
                    søknadInfos = setOf(
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            søknad1JournalpostId,
                            LocalDate.parse("2024-05-23").atStartOfDay(ZoneId.systemDefault()),
                            Kildesystem.SØKNADSDIALOG,
                            InnsendingType.SØKNAD
                        ),
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            ettersendelseJournalpostId,
                            LocalDate.parse("2024-05-24").atStartOfDay(ZoneId.systemDefault()),
                            Kildesystem.SØKNADSDIALOG,
                            InnsendingType.ETTERSENDELSE
                        )
                    ),
                ),
                lagBehandlingDAO(
                    opprettetTidspunkt = ZonedDateTime.now(),
                    behandlingStatus = BehandlingStatus.OPPRETTET,
                    søknadInfos = setOf(
                        InnsendingInfo(
                            InnsendingStatus.MOTTATT,
                            ettersendelseJournalpostId,
                            LocalDate.parse("2024-05-24").atStartOfDay(ZoneId.systemDefault()),
                            Kildesystem.SØKNADSDIALOG,
                            InnsendingType.ETTERSENDELSE
                        )
                    )
                )
            ).stream()
        }
        every { innsendingService.hentSøknad(any()) } returns lagPsbSøknad(søknad1JournalpostId)
        every { innsendingService.hentSøknad(any()) } returns lagPsbSøknad(søknad2JournalpostId)
        every { innsendingService.hentEttersendelse(any()) } returns lagEttersendelse(ettersendelseJournalpostId)
        every { dokumentService.hentDokumentOversikt() } returns listOf(
            lagDokumentDto(søknad1JournalpostId),
            lagDokumentDto(søknad2JournalpostId),
            lagDokumentDto(ettersendelseJournalpostId)
        ) //ikke mulig i praksis

        val sak = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)

        assertThat(sak).hasSize(1)
        assertThat(sak.first().sak.utledetStatus.status).isEqualTo(BehandlingStatus.AVSLUTTET)
    }

    @Test
    fun `Forvent saksbehandlingstid oppgitt i uker`() {
        val saksbehandlingstid = sakService.hentGenerellSaksbehandlingstid()
        Assertions.assertThat(saksbehandlingstid.saksbehandlingstidUker).isEqualTo(7)
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
        url = URI("http://localhost:8080/dokument/journalpostId1/dokumentInfo1/ARKIV").toURL()
    )

    private fun lagPsbSøknad(journalpostId: String) = PsbSøknadDAO(
        journalpostId = journalpostId,
        søkerAktørId = "sak1234",
        pleietrengendeAktørId = "søknadId",
        søknad = JsonUtils.toString(lagSøknad())
    )

    private fun lagEttersendelse(ettersendelseJournalpostId: String): EttersendelseDAO = EttersendelseDAO(
        journalpostId = ettersendelseJournalpostId,
        søkerAktørId = "sak1234",
        pleietrengendeAktørId = "søknadId",
        ettersendelse = JsonUtils.toString(lagEttersendelse())
    )

    private fun lagSøknad() = Søknad(
        SøknadId.of(UUID.randomUUID().toString()),
        Versjon.of("1.0.0"),
        ZonedDateTime.now(),
        Søker(NorskIdentitetsnummer.of("22222222222")),
        PleiepengerSyktBarn()
    )

    private fun lagEttersendelse() = Ettersendelse.builder()
        .søknadId(SøknadId.of(UUID.randomUUID().toString()))
        .mottattDato(ZonedDateTime.now())
        .type(EttersendelseType.LEGEERKLÆRING)
        .ytelse(Ytelse.PLEIEPENGER_SYKT_BARN)
        .søker(Søker(NorskIdentitetsnummer.of("22222222222")))
        .pleietrengende(Pleietrengende(NorskIdentitetsnummer.of("11111111111")))
        .build()

    private fun lagBehandlingDAO(
        søknadInfos: Set<InnsendingInfo>, opprettetTidspunkt: ZonedDateTime?, behandlingStatus: BehandlingStatus,
    ): BehandlingDAO {
        return BehandlingDAO(
            UUID.randomUUID(),
            hovedSøkerAktørId,
            barn1AktørId,
            "sak1234",
            JsonUtils.toString(lagBehandling(søknadInfos, opprettetTidspunkt, behandlingStatus)),
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN
        )
    }

    private fun lagBehandling(
        søknadInfos: Set<InnsendingInfo>, opprettetTidspunkt: ZonedDateTime?, behandlingStatus: BehandlingStatus,
    ): Behandling {
        return Behandling(
            UUID.randomUUID(),
            opprettetTidspunkt,
            ZonedDateTime.now(),
            BehandlingResultat.INNVILGET,
            behandlingStatus,
            søknadInfos,
            setOf(
                Aksjonspunkt(Aksjonspunkt.Venteårsak.INNTEKTSMELDING, opprettetTidspunkt)
            ),
            false,
            Fagsak(
                Saksnummer("ABC123"),
                AktørId("11111111111"),
                AktørId("22222222222"),
                FagsakYtelseType.PLEIEPENGER_SYKT_BARN
            )
        )
    }
}
