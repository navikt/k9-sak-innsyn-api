package no.nav.sifinnsynapi.utils

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.personopplysninger.Søker
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.sifinnsynapi.soknad.SøknadDTO
import java.util.*


fun List<SøknadDTO>.somJson(mapper: ObjectMapper) = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
fun SøknadDTO.somJson(mapper: ObjectMapper) = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
fun Søknad.somJson(mapper: ObjectMapper) = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)

fun defaultHendelse(søknadId: UUID = UUID.randomUUID(), søkerNorskIdentitetsnummer: String = "12345678910") = Søknad()
    .medSøknadId(søknadId.toString())
    .medSøker(Søker(NorskIdentitetsnummer.of(søkerNorskIdentitetsnummer)))
