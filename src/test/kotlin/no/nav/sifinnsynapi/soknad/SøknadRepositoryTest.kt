package no.nav.sifinnsynapi.soknad

import no.nav.k9.søknad.Søknad
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.common.AktørId
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
    fun `Sjekke om søknad eksisterer ved bruk av søknadId`() {
        val søknadDAO = lagSøknadDAO()
        repository.save(søknadDAO)

        val eksistererSøknad = repository.existsBySøknadId(søknadDAO.søknadId)

        assertTrue(eksistererSøknad)
    }

    private fun lagSøknadDAO(søknadId: UUID = UUID.randomUUID(), aktørId: String = "123456"): SøknadDAO = SøknadDAO(
        id = UUID.randomUUID(),
        søknadId = søknadId,
        aktørId = AktørId(aktørId),
        søknad = Søknad()
            .medSøknadId(søknadId.toString()),
        opprettet = ZonedDateTime.now()
    )
}
