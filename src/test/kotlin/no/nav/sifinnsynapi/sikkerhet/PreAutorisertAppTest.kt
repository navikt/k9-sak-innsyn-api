package no.nav.sifinnsynapi.sikkerhet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PreAutorisertAppTest {

    //language=json
    private val preAuthorizedApps = """
        [
          { "name": "dev-gcp:dusseldorf:sif-innsyn-api", "clientId": "sif-innsyn-api-client-id" },
          { "name": "dev-fss:k9saksbehandling:k9-sak", "clientId": "k9-sak-client-id" }
        ]
    """

    @Test
    fun `finner client-id for appen med riktig navn`() {
        assertEquals(
            "k9-sak-client-id",
            clientIdForPreAutorisertApp("dev-fss:k9saksbehandling:k9-sak", preAuthorizedApps)
        )
    }

    @Test
    fun `feiler når appen ikke er pre-autorisert`() {
        assertThrows<IllegalStateException> {
            clientIdForPreAutorisertApp("prod-fss:k9saksbehandling:k9-sak", preAuthorizedApps)
        }
    }
}
