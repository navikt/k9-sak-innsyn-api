package no.nav.sifinnsynapi.sak

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.k9.innsyn.sak.Aksjonspunkt
import no.nav.k9.innsyn.sak.AktørId
import no.nav.k9.innsyn.sak.Behandling
import no.nav.k9.innsyn.sak.BehandlingResultat
import no.nav.k9.innsyn.sak.BehandlingStatus
import no.nav.k9.innsyn.sak.Fagsak
import no.nav.k9.innsyn.sak.FagsakYtelseType
import no.nav.k9.innsyn.sak.InnsendingInfo
import no.nav.k9.innsyn.sak.InnsendingStatus
import no.nav.k9.innsyn.sak.InnsendingType
import no.nav.k9.innsyn.sak.Saksnummer
import no.nav.k9.søknad.JsonUtils
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.dokumentoversikt.DokumentService
import no.nav.sifinnsynapi.omsorg.OmsorgDAO
import no.nav.sifinnsynapi.omsorg.OmsorgRepository
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.oppslag.SøkerOppslagRespons
import no.nav.sifinnsynapi.sak.behandling.BehandlingDAO
import no.nav.sifinnsynapi.sak.behandling.BehandlingService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
@ActiveProfiles("test")
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK) // Integrasjonstest - Kjører opp hele Spring Context med alle konfigurerte beans.
class SakServiceITest {

    @Autowired
    private lateinit var sakService: SakService

    @Autowired
    private lateinit var omsorgRepository: OmsorgRepository

    @MockkBean(relaxed = true)
    private lateinit var oppslagsService: OppslagsService

    @MockkBean(relaxed = true)
    lateinit var dokumentService: DokumentService

    @MockkBean(relaxed = true)
    lateinit var behandlingService: BehandlingService

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

        omsorgRepository.saveAll(
            listOf(
                OmsorgDAO(
                    id = "1",
                    søkerAktørId = hovedSøkerAktørId,
                    pleietrengendeAktørId = barn1AktørId,
                    harOmsorgen = false,
                    opprettetDato = ZonedDateTime.now(ZoneOffset.UTC),
                    oppdatertDato = ZonedDateTime.now(ZoneOffset.UTC)
                ),
                OmsorgDAO(
                    id = "2",
                    søkerAktørId = hovedSøkerAktørId,
                    pleietrengendeAktørId = barn2AktørId,
                    harOmsorgen = false,
                    opprettetDato = ZonedDateTime.now(ZoneOffset.UTC),
                    oppdatertDato = ZonedDateTime.now(ZoneOffset.UTC)
                )
            )
        )
    }

    @AfterEach
    fun tearDown() {
        omsorgRepository.deleteAll()
    }

    @AfterAll
    fun cleanup() {
        omsorgRepository.deleteAll()
    }

    @Test
    fun `gitt søker har omsorgen for barnet, forvent saker`() {
        omsorgRepository.oppdaterOmsorg(true, hovedSøkerAktørId, barn1AktørId)
        omsorgRepository.oppdaterOmsorg(false, hovedSøkerAktørId, barn2AktørId)

        every { oppslagsService.systemoppslagBarn(any()) } returns listOf(
            BarnOppslagDTO(
                aktørId = barn1AktørId,
                fødselsdato = LocalDate.parse("2005-02-12"),
                fornavn = "Ole",
                mellomnavn = null,
                etternavn = "Doffen",
                identitetsnummer = "12020567099"
            )
        )

        val saksnummer = "sak1234"
        val journalpostId = "123456789"

        every { dokumentService.hentDokumentOversikt() } returns listOf(
            DokumentDTO(
                journalpostId = journalpostId,
                dokumentInfoId = "123456789",
                saksnummer = Saksnummer(saksnummer),
                tittel = "Søknad om pleiepenger til sykt barn",
                dokumentType = DokumentBrevkode.PLEIEPENGER_SYKT_BARN_SOKNAD,
                filtype = "PDFA",
                harTilgang = true,
                url = URL("http://localhost:8080/saker/123456789"),
                journalposttype = Journalposttype.INNGÅENDE,
                relevanteDatoer = listOf(
                    RelevantDatoDTO(
                        dato = LocalDate.now().toString(),
                        datotype = Datotype.DATO_OPPRETTET
                    )
                )
            )
        )

        every { behandlingService.hentBehandlinger(any(), any()) } answers {
            listOf(
                BehandlingDAO(
                    behandlingId = UUID.randomUUID(),
                    søkerAktørId = hovedSøkerAktørId,
                    pleietrengendeAktørId = barn1AktørId,
                    saksnummer = saksnummer,
                    behandling = JsonUtils.toString(lagBehandling(setOf(
                        InnsendingInfo(InnsendingStatus.MOTTATT, journalpostId, ZonedDateTime.now(), null, InnsendingType.SØKNAD)
                    ))),
                    ytelsetype = FagsakYtelseType.PLEIEPENGER_SYKT_BARN
                )
            ).stream()
        }

        val hentSaker = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        Assertions.assertThat(hentSaker).isNotEmpty
        Assertions.assertThat(hentSaker).size().isEqualTo(1)
    }

    @Test
    fun `gitt søker ikke har omsorgen for barnet, forvent innsyn i sak og at barnets navn anonymiseres`() {
        omsorgRepository.oppdaterOmsorg(false, hovedSøkerAktørId, barn1AktørId)
        omsorgRepository.oppdaterOmsorg(false, hovedSøkerAktørId, barn2AktørId)

        every { oppslagsService.systemoppslagBarn(any()) } returns listOf(
            BarnOppslagDTO(
                aktørId = barn1AktørId,
                fødselsdato = LocalDate.parse("2005-02-12"),
                fornavn = "Ole",
                mellomnavn = null,
                etternavn = "Doffen",
                identitetsnummer = "12020567099"
            )
        )

        val saksnummer = "sak1234"
        val journalpostId = "123456789"

        every { dokumentService.hentDokumentOversikt() } returns listOf(
            DokumentDTO(
                journalpostId = journalpostId,
                dokumentInfoId = "123456789",
                saksnummer = Saksnummer(saksnummer),
                tittel = "Søknad om pleiepenger til sykt barn",
                dokumentType = DokumentBrevkode.PLEIEPENGER_SYKT_BARN_SOKNAD,
                filtype = "PDFA",
                harTilgang = true,
                url = URL("http://localhost:8080/saker/123456789"),
                journalposttype = Journalposttype.INNGÅENDE,
                relevanteDatoer = listOf(
                    RelevantDatoDTO(
                        dato = LocalDate.now().toString(),
                        datotype = Datotype.DATO_OPPRETTET
                    )
                )
            )
        )

        every { behandlingService.hentBehandlinger(any(), any()) } answers {
            listOf(
                BehandlingDAO(
                    behandlingId = UUID.randomUUID(),
                    søkerAktørId = hovedSøkerAktørId,
                    pleietrengendeAktørId = barn1AktørId,
                    saksnummer = saksnummer,
                    behandling = JsonUtils.toString(lagBehandling(setOf(
                        InnsendingInfo(InnsendingStatus.MOTTATT, journalpostId, ZonedDateTime.now(), null, InnsendingType.SØKNAD)
                    ))),
                    ytelsetype = FagsakYtelseType.PLEIEPENGER_SYKT_BARN
                )
            ).stream()
        }

        val hentSaker = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        Assertions.assertThat(hentSaker).isNotEmpty
        Assertions.assertThat(hentSaker).size().isEqualTo(1)
        Assertions.assertThat(hentSaker.first().pleietrengende.fornavn).isNull()
        Assertions.assertThat(hentSaker.first().pleietrengende.mellomnavn).isNull()
        Assertions.assertThat(hentSaker.first().pleietrengende.etternavn).isNull()
    }

    @Test
    fun `gitt søker har omsorgen for barnet, men barnet er addressebeskyttet, forvent ingen innsyn`() {
        omsorgRepository.oppdaterOmsorg(true, hovedSøkerAktørId, barn1AktørId)
        every { oppslagsService.systemoppslagBarn(any()) } returns listOf()

        val hentSaker = sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        Assertions.assertThat(hentSaker).isEmpty()
    }

    @Test
    fun `gitt at søker ikke har barn, forvent tom liste`() {
        every { oppslagsService.hentBarn() } answers { listOf() }
        Assertions.assertThat(sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)).isEmpty()
    }

    @Test
    fun `gitt at søker ikke har omsorg for barna, forvent tom liste med saker`() {
        omsorgRepository.oppdaterOmsorg(false, hovedSøkerAktørId, barn1AktørId)
        omsorgRepository.oppdaterOmsorg(false, hovedSøkerAktørId, barn2AktørId)

        org.junit.jupiter.api.Assertions.assertFalse(
            omsorgRepository.findBySøkerAktørIdAndPleietrengendeAktørId(
                hovedSøkerAktørId,
                barn1AktørId
            )!!.harOmsorgen
        )
        org.junit.jupiter.api.Assertions.assertFalse(
            omsorgRepository.findBySøkerAktørIdAndPleietrengendeAktørId(
                hovedSøkerAktørId,
                barn2AktørId
            )!!.harOmsorgen
        )

        Assertions.assertThat(sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)).isEmpty()

    }

    @Test
    fun `Forvent saksbehandlingstid oppgitt i uker`() {
        val saksbehandlingstid = sakService.hentGenerellSaksbehandlingstid()
        Assertions.assertThat(saksbehandlingstid.saksbehandlingstidUker).isEqualTo(6)
    }

    private fun lagBehandling(søknadInfos: Set<InnsendingInfo>): Behandling {
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
