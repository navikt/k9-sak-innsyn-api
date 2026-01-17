package no.nav.sifinnsynapi

import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock

@SpringBootTest(classes = [SifInnsynApiApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnableMockOAuth2Server
@EnableWireMock(ConfigureWireMock())
class K9SakInnsynApiApplicationTests {

	@Test
	fun contextLoads() {
	}

}
