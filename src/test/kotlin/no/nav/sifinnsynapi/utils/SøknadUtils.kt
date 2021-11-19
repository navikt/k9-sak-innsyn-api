package no.nav.sifinnsynapi.utils

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.k9.innsyn.InnsynHendelse
import no.nav.k9.innsyn.PsbSøknadsinnhold
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.Versjon
import no.nav.k9.søknad.felles.personopplysninger.Barn
import no.nav.k9.søknad.felles.personopplysninger.Søker
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.k9.søknad.ytelse.psb.v1.PleiepengerSyktBarn
import no.nav.sifinnsynapi.soknad.SøknadDTO
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.*


fun List<SøknadDTO>.somJson(mapper: ObjectMapper) = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
fun SøknadDTO.somJson(mapper: ObjectMapper) = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
fun InnsynHendelse<PsbSøknadsinnhold>.somJson(mapper: ObjectMapper) =
    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)

fun defaultPsbSøknadInnholdHendelse(
    søknadId: UUID = UUID.randomUUID(),
    journalpostId: String = "1",
    søkerNorskIdentitetsnummer: String = "14026223262",
    søkerAktørId: String = "1",
    barnNorskIdentitetsnummer: String = "21121879023",
    pleiepetrengendeSøkerAktørId: String = "2"
) = InnsynHendelse<PsbSøknadsinnhold>(
    ZonedDateTime.now(UTC),
    PsbSøknadsinnhold(
        journalpostId, søkerAktørId, pleiepetrengendeSøkerAktørId,
        Søknad()
            .medVersjon(Versjon.of("1.0.0"))
            .medMottattDato(ZonedDateTime.now(UTC))
            .medSøknadId(søknadId.toString())
            .medSøker(Søker(NorskIdentitetsnummer.of(søkerNorskIdentitetsnummer)))
            .medYtelse(
                PleiepengerSyktBarn()
                    .medBarn(Barn().medNorskIdentitetsnummer(NorskIdentitetsnummer.of(barnNorskIdentitetsnummer)))
            )
    )
)
