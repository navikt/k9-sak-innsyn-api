package no.nav.sifinnsynapi.soknad

import assertk.assertions.isEqualTo
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.personopplysninger.Søker
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.Assert.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
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
    fun `hent alle søknader som stream`() {
        assertNotNull(repository.save(lagSøknadDAO()))
        assertk.assertThat(repository.hentSøknaderSortertPåOppdatertTidspunkt(listOf("10987654321")).count()).isEqualTo(1)
    }

    @Test
    fun `hent 1000 søknader som en strøm`() {
        IntStream.range(0, 1000).forEach {
            repository.save(lagSøknadDAO(journalpostId = it.toString()))
        }
        repository.hentSøknaderSortertPåOppdatertTidspunkt(listOf("10987654321")).use {
            assertk.assertThat(it.count()).isEqualTo(1000)
        }
    }

    private fun lagSøknadDAO(
        søknadId: UUID = UUID.randomUUID(),
        journalpostId: String = "00000000001",
        søkerPersonIdentifikator: String = "14026223262",
        søkerAktørId: String = "12345678910",
        pleietrengendeAktørId: String = "10987654321"
    ): PsbSøknadDAO = PsbSøknadDAO(
        journalpostId = journalpostId,
        søkerAktørId = søkerAktørId,
        pleietrengendeAktørId = pleietrengendeAktørId,
        søknad = JsonUtils.toString(
            Søknad()
                .medSøknadId(søknadId.toString())
                .medSøker(Søker(NorskIdentitetsnummer.of(søkerPersonIdentifikator)))
        ),
        opprettetDato = ZonedDateTime.now()
    )
}
