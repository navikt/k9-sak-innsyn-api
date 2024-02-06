package no.nav.sifinnsynapi.sak

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.omsorg.OmsorgDAO
import no.nav.sifinnsynapi.omsorg.OmsorgRepository
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.oppslag.SøkerOppslagRespons
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
@ActiveProfiles("test")
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK) // Integrasjonstest - Kjører opp hele Spring Context med alle konfigurerte beans.
class SakServiceTest {

    @Autowired
    private lateinit var sakService: SakService

    @Autowired
    private lateinit var omsorgRepository: OmsorgRepository

    @MockkBean(relaxed = true)
    private lateinit var oppslagsService: OppslagsService

    private companion object {
        private val annenSøkerAktørId = "00000000000"
        private val hovedSøkerAktørId = "11111111111"
        private val barn1AktørId = "22222222222"
        private val barn2AktørId = "33333333333"
    }

    @BeforeEach
    fun setUp() {
        every { oppslagsService.hentAktørId() } returns SøkerOppslagRespons(aktørId = hovedSøkerAktørId)
        every { oppslagsService.hentBarn() } returns listOf(
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
    @Disabled("Aktiver igjen når hentSaker returnerer data")
    fun `gitt søker har omsorgen for barnet, forvent saker`() {
        omsorgRepository.oppdaterOmsorg(true, hovedSøkerAktørId, barn1AktørId)
        omsorgRepository.oppdaterOmsorg(false, hovedSøkerAktørId, barn2AktørId)

        val hentSaker = sakService.hentSaker()
        Assertions.assertThat(hentSaker).isNotEmpty
        Assertions.assertThat(hentSaker).size().isEqualTo(1)
    }

    @Test
    fun `gitt at søker ikke har barn, forvent tom liste`() {
        every { oppslagsService.hentBarn() } answers { listOf() }
        Assertions.assertThat(sakService.hentSaker()).isEmpty()
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

        Assertions.assertThat(sakService.hentSaker()).isEmpty()

    }

    @Test
    fun `Forvent saksbehandlingstid oppgitt i uker`() {
        val saksbehandlingstid = sakService.hentGenerellSaksbehandlingstid()
        Assertions.assertThat(saksbehandlingstid.saksbehandlingstidUker).isEqualTo(7)
    }
}
