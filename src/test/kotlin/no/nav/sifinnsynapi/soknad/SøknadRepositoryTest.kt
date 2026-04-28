package no.nav.sifinnsynapi.soknad

import assertk.assertions.isEqualTo
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.personopplysninger.Søker
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.util.K9Jackson2ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.ZonedDateTime
import java.util.*
import java.util.stream.IntStream

@DataJpaTest
@ActiveProfiles("test")
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test. Se application-test.yml -> no.nav.security.jwt.issuer.tokenx for konfigurasjon
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
class SøknadRepositoryTest {

    @Autowired
    lateinit var repository: SøknadRepository // Repository som brukes til databasekall.

    @BeforeAll
    internal fun setUp() {
        assertNotNull(repository)
        repository.deleteAll() //Tømmer databasen mellom hver test
    }

    @AfterEach
    internal fun tearDown() {
        repository.deleteAll() //Tømmer databasen mellom hver test
    }

    @Test
    fun `happy case`() {
        assertNotNull(repository.save(lagSøknadDAO()))
    }

    @Test
    fun `hent alle søknader`() {
        assertNotNull(repository.save(lagSøknadDAO()))
        assertk.assertThat(repository.findAllBySøkerAktørIdAndPleietrengendeAktørIdOrderByOppdatertDatoAsc("12345678910", "10987654321").size)
            .isEqualTo(1)
    }

    @Test
    fun `hent alle søknader kun med søkers aktørId`() {
        repository.save(lagSøknadDAO(journalpostId = "00000000001", pleietrengendeAktørId = "10987654321"))
        repository.save(lagSøknadDAO(journalpostId = "00000000002", pleietrengendeAktørId = "10987654322"))
        assertk.assertThat(repository.findAllBySøkerAktørIdOrderByOppdatertDatoAsc("12345678910").size)
            .isEqualTo(2)
    }

    @Test
    fun `oppdater aktørid`() {
        assertNotNull(repository.save(lagSøknadDAO()))
        assertk.assertThat(repository.oppdaterAktørIdForSøker("12345678911","12345678910"))
            .isEqualTo(1)
    }

    @Test
    fun `hent 1000 søknader`() {
        IntStream.range(0, 1000).forEach {
            repository.save(lagSøknadDAO(journalpostId = it.toString()))
        }
        assertk.assertThat(
            repository.findAllBySøkerAktørIdAndPleietrengendeAktørIdOrderByOppdatertDatoAsc("12345678910", "10987654321").size
        ).isEqualTo(1000)
    }

    private fun lagSøknadDAO(
        søknadId: UUID = UUID.randomUUID(),
        journalpostId: String = "00000000001",
        søkerPersonIdentifikator: String = "23500180528",
        søkerAktørId: String = "12345678910",
        pleietrengendeAktørId: String = "10987654321",
    ): PsbSøknadDAO = PsbSøknadDAO(
        journalpostId = journalpostId,
        søkerAktørId = søkerAktørId,
        pleietrengendeAktørId = pleietrengendeAktørId,
        søknad = K9Jackson2ObjectMapper.toString(
            Søknad()
                .medSøknadId(søknadId.toString())
                .medSøker(Søker(NorskIdentitetsnummer.of(søkerPersonIdentifikator)))
        ),
        opprettetDato = ZonedDateTime.now()
    )
}
