package no.nav.sifinnsynapi.soknad

import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import no.nav.k9.innsyn.Søknadsammenslåer
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * For feilsøking av sammenslåtte søknader.
 *
 *
 */
class SøknadSammenslåerTest {

    @Test
    @Disabled("Kjør lokalt for å feilsøke sammenslåtte søknader")
    fun test() {
        val søkerIdent = TODO("Fyll inn søkerident her")
        val søknadList = jsonToSøknad()
        println("Slår sammen ${søknadList.size} søknader")
        val sammenslåttSøknad = søknadList.stream()
            .map { søknad -> kunPleietrengendeDataFraAndreSøkere(søkerIdent, søknad) }
            .reduce(Søknadsammenslåer::slåSammen)
            .orElse(null)

        println("Sammenslått søknad: ${JsonUtils.toString(sammenslåttSøknad)}")
    }
}

private fun kunPleietrengendeDataFraAndreSøkere(søkerIdent: String, søknad: Søknad) =
    when (søknad.søker.personIdent.verdi) {
        søkerIdent -> søknad
        else -> Søknadsammenslåer.kunPleietrengendedata(søknad)
    }

fun jsonToSøknad(): List<Søknad> {
    //language=json
    val json = """
        
    """.trimIndent() // Fyll inn søknad her
    return JsonUtils.getObjectMapper().readValue(json, jacksonTypeRef())
}

