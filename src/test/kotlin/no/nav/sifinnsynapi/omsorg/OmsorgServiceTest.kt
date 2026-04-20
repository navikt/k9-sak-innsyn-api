package no.nav.sifinnsynapi.omsorg

import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
@ActiveProfiles("test")
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK) // Integrasjonstest - Kjører opp hele Spring Context med alle konfigurerte beans.
internal class OmsorgServiceTest {

    @Autowired
    lateinit var omsorgService: OmsorgService

    @Autowired
    lateinit var omsorgRepository: OmsorgRepository

    @BeforeEach
    fun setUp() {
        omsorgRepository.deleteAll()
    }

    @AfterAll
    internal fun tearDown() {
        omsorgRepository.deleteAll()
    }

    @Test
    internal fun `gitt at søker har omsorg for pleiepetrengende, forvent HAR_OMSORGEN`() {
        val søkerAktørId = "11111111111"
        val pleietrengendeAktørId = "22222222222"
        omsorgRepository.save(
            OmsorgDAO(
                id = "1",
                søkerAktørId = søkerAktørId,
                pleietrengendeAktørId = pleietrengendeAktørId,
                harOmsorgen = true,
                opprettetDato = ZonedDateTime.now(UTC),
                oppdatertDato = ZonedDateTime.now(UTC)
            )
        )

        val omsorgStatus = omsorgService.hentOmsorgStatus(
            søkerAktørId = søkerAktørId,
            pleietrengendeAktørId = pleietrengendeAktørId
        )

        assertEquals(OmsorgStatus.HAR_OMSORGEN, omsorgStatus)
    }

    @Test
    internal fun `gitt at søker ikke har omsorg for pleiepetrengende, forvent HAR_IKKE_OMSORGEN`() {
        val søkerAktørId = "11111111111"
        val pleietrengendeAktørId = "22222222222"
        omsorgRepository.save(
            OmsorgDAO(
                id = "1",
                søkerAktørId = søkerAktørId,
                pleietrengendeAktørId = pleietrengendeAktørId,
                harOmsorgen = false,
                opprettetDato = ZonedDateTime.now(UTC),
                oppdatertDato = ZonedDateTime.now(UTC)
            )
        )

        val omsorgStatus = omsorgService.hentOmsorgStatus(
            søkerAktørId = søkerAktørId,
            pleietrengendeAktørId = pleietrengendeAktørId
        )

        assertEquals(OmsorgStatus.HAR_IKKE_OMSORGEN, omsorgStatus)
    }

    @Test
    internal fun `gitt at omsorg ikke er evaluert, forvent HAR_IKKE_EVALUERT_OMSORGEN`() {
        val søkerAktørId = "11111111111"
        val pleietrengendeAktørId = "22222222222"

        val omsorgStatus = omsorgService.hentOmsorgStatus(
            søkerAktørId = søkerAktørId,
            pleietrengendeAktørId = pleietrengendeAktørId
        )

        assertEquals(OmsorgStatus.HAR_IKKE_EVALUERT_OMSORGEN, omsorgStatus)
    }

    @Test
    fun `gitt at omsorg for pleiepetrengende oppdateres, forvent HAR_OMSORGEN`() {
        val søkerAktørId = "11111111111"
        val pleietrengendeAktørId = "22222222222"
        omsorgRepository.save(
            OmsorgDAO(
                id = "1",
                søkerAktørId = søkerAktørId,
                pleietrengendeAktørId = pleietrengendeAktørId,
                harOmsorgen = false,
                opprettetDato = ZonedDateTime.now(UTC),
                oppdatertDato = ZonedDateTime.now(UTC)
            )
        )

        val omsorgStatus = omsorgService.hentOmsorgStatus(
            søkerAktørId = søkerAktørId,
            pleietrengendeAktørId = pleietrengendeAktørId
        )
        assertEquals(OmsorgStatus.HAR_IKKE_OMSORGEN, omsorgStatus)

        assertTrue { omsorgService.oppdaterOmsorg(søkerAktørId, pleietrengendeAktørId, true) }
        val omsorgStatus2 = omsorgService.hentOmsorgStatus(
            søkerAktørId = søkerAktørId,
            pleietrengendeAktørId = pleietrengendeAktørId
        )
        assertEquals(OmsorgStatus.HAR_OMSORGEN, omsorgStatus2)
    }
}
