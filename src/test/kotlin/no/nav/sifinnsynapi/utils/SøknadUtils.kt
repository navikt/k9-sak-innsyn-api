package no.nav.sifinnsynapi.utils

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.k9.innsyn.InnsynHendelse
import no.nav.k9.innsyn.PsbSøknadsinnhold
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.Versjon
import no.nav.k9.søknad.felles.personopplysninger.Barn
import no.nav.k9.søknad.felles.personopplysninger.Søker
import no.nav.k9.søknad.felles.type.*
import no.nav.k9.søknad.ytelse.psb.v1.PleiepengerSyktBarn
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstaker
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstid
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidInfo
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidPeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.tilsyn.TilsynPeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.tilsyn.Tilsynsordning
import no.nav.sifinnsynapi.soknad.SøknadDTO
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.*


fun List<SøknadDTO>.somJson(mapper: ObjectMapper) = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
fun SøknadDTO.somJson(mapper: ObjectMapper) = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
fun InnsynHendelse<PsbSøknadsinnhold>.somJson(mapper: ObjectMapper) =
    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)

fun Søknad.somJson(): String = JsonUtils.toString(this)

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

fun defaultSøknad(
    søknadId: UUID = UUID.randomUUID(),
    søknadsPeriode: Periode = Periode(LocalDate.now().plusDays(5), LocalDate.now().plusDays(5)),
    søkersIdentitetsnummer: String = "14026223262",
    arbeidstid: Arbeidstid = Arbeidstid().medArbeidstaker(listOf(defaultArbeidstid(listOf(søknadsPeriode)))),
    tilsynsordning: Tilsynsordning = defaultTilsynsordning(
        mapOf(
            Periode(
                LocalDate.parse("2021-08-12"),
                LocalDate.parse("2021-08-13")
            ) to TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(Duration.ofHours(2))
        )
    ),
    ytelse: PleiepengerSyktBarn = defaultPleiepengerSyktBarn(
        søknadsPeriode = søknadsPeriode,
        arbeidstid = arbeidstid,
        tilsynsordning = tilsynsordning
    )
): Søknad = Søknad()
    .medSøknadId(søknadId.toString())
    .medSøker(Søker(NorskIdentitetsnummer.of(søkersIdentitetsnummer)))
    .medJournalpost(Journalpost().medJournalpostId("123456789"))
    .medSpråk(Språk.NORSK_BOKMÅL)
    .medMottattDato(ZonedDateTime.now())
    .medVersjon(Versjon.of("1.0.0"))
    .medYtelse(ytelse)

fun defaultPleiepengerSyktBarn(
    søknadsPeriode: Periode = Periode(LocalDate.now().plusDays(5), LocalDate.now().plusDays(5)),
    barnNorskIdentitetsnummer: String = "21121879023",
    arbeidstid: Arbeidstid = Arbeidstid().medArbeidstaker(listOf(defaultArbeidstid(listOf(søknadsPeriode)))),
    tilsynsordning: Tilsynsordning = defaultTilsynsordning(
        mapOf(
            Periode(
                LocalDate.parse("2021-08-12"),
                LocalDate.parse("2021-08-13")
            ) to TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(
                Duration.ofHours(2)
            ),
            Periode(
                LocalDate.parse("2021-09-01"),
                LocalDate.parse("2021-09-15")
            ) to TilsynPeriodeInfo().medEtablertTilsynTimerPerDag(
                Duration.ofHours(7).plusMinutes(30)
            )
        )
    )
) = PleiepengerSyktBarn()
    .medSøknadsperiode(søknadsPeriode)
    .medBarn(Barn().medNorskIdentitetsnummer(NorskIdentitetsnummer.of(barnNorskIdentitetsnummer)))
    .medArbeidstid(arbeidstid)
    .medTilsynsordning(tilsynsordning)

fun defaultArbeidstid(periodeList: List<Periode>): Arbeidstaker {
    val arbeidstidPeriodeInfo =
        ArbeidstidPeriodeInfo()
            .medJobberNormaltTimerPerDag(Duration.ofHours(7).plusMinutes(30))
            .medFaktiskArbeidTimerPerDag(Duration.ofHours(3))
    return Arbeidstaker()
        .medOrganisasjonsnummer(Organisasjonsnummer.of("999999999"))
        .medArbeidstidInfo(
            ArbeidstidInfo()
                .medPerioder(lagPerioder(periodeList, arbeidstidPeriodeInfo))
        )
}

fun defaultArbeidstaker(
    organisasjonsnummer: String,
    periode: Periode,
    normaltTimerPerDag: Long,
    faktiskArbeidTimerPerDag: Long
): Arbeidstaker =
    Arbeidstaker()
        .medOrganisasjonsnummer(Organisasjonsnummer.of(organisasjonsnummer))
        .medArbeidstidInfo(
            ArbeidstidInfo().medPerioder(
                mapOf(
                    periode to ArbeidstidPeriodeInfo()
                        .medJobberNormaltTimerPerDag(Duration.ofHours(normaltTimerPerDag))
                        .medFaktiskArbeidTimerPerDag(Duration.ofHours(faktiskArbeidTimerPerDag))
                )
            )
        )

fun defaultTilsynsordning(perioder: Map<Periode, TilsynPeriodeInfo>): Tilsynsordning {
    return Tilsynsordning().medPerioder(perioder)
}

fun <T> lagPerioder(periodeList: List<Periode>, periodeInfo: T): HashMap<Periode, T> {
    val resultatMap = HashMap<Periode, T>()
    periodeList.forEach { periode ->
        resultatMap[periode] = periodeInfo
    }
    return resultatMap
}
