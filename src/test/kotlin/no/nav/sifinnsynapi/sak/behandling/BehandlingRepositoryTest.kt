package no.nav.sifinnsynapi.sak.behandling

import no.nav.k9.innsyn.sak.FagsakYtelseType
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.ZonedDateTime
import java.util.*

@DataJpaTest
@ActiveProfiles("test")
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test. Se application-test.yml -> no.nav.security.jwt.issuer.tokenx for konfigurasjon
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
class BehandlingRepositoryTest {

    @Autowired
    lateinit var repository: BehandlingRepository // Repository som brukes til databasekall.

    @BeforeAll
    internal fun setUp() {
        Assertions.assertNotNull(repository)
        repository.deleteAll() //Tømmer databasen mellom hver test
    }

    @AfterEach
    internal fun tearDown() {
        repository.deleteAll() //Tømmer databasen mellom hver test
    }

    @Test
    fun `lagring og lesing feiler ikke`() {
        val behandlingDAO = lagBehandlingDAO()
        Assertions.assertNotNull(repository.save(behandlingDAO))
        val behandling = repository.findById(behandlingDAO.behandlingId)
        Assertions.assertTrue(behandling.isPresent)
    }

    private fun lagBehandlingDAO(
        søkerAktørId: String = "12345678910",
        pleietrengendeAktørId: String = "10987654321",
        saksnummer: String = "123456789",
        yteselsetype: FagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
        behandlingJson: String = "{}"
    ): BehandlingDAO = BehandlingDAO(
        behandlingId = UUID.randomUUID(),
        søkerAktørId = søkerAktørId,
        pleietrengendeAktørId = pleietrengendeAktørId,
        saksnummer = saksnummer,
        behandling = behandlingJson,
        ytelsetype = yteselsetype,
        opprettetDato = ZonedDateTime.now(),
        oppdatertDato = ZonedDateTime.now()
    )
}
