package no.nav.sifinnsynapi.utils

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
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
import org.assertj.core.api.Assertions
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.*
import java.util.stream.Collectors


fun List<SøknadDTO>.somJson(mapper: ObjectMapper) = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
fun SøknadDTO.somJson(mapper: ObjectMapper) = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
fun InnsynHendelse<PsbSøknadsinnhold>.somJson() = JsonUtils.toString(this)

fun Søknad.somJson(): String = JsonUtils.toString(this)

fun defaultPsbSøknadInnholdHendelse(
    søknadId: UUID = UUID.randomUUID(),
    oppdateringsTidspunkt: ZonedDateTime = ZonedDateTime.now(UTC),
    journalpostId: String = "1",
    søkerAktørId: String = "1",
    pleiepetrengendeAktørId: String = "2",
    søknadsPeriode: Periode = Periode(LocalDate.now().plusDays(5), LocalDate.now().plusDays(5)),
    arbeidstid: Arbeidstid? = null,
    tilsynsordning: Tilsynsordning? = null
) = InnsynHendelse<PsbSøknadsinnhold>(
    oppdateringsTidspunkt,
    PsbSøknadsinnhold(
        journalpostId, søkerAktørId, pleiepetrengendeAktørId,
        defaultSøknad(
            søknadId = søknadId,
            søknadsPeriode = søknadsPeriode,
            arbeidstid = arbeidstid,
            tilsynsordning = tilsynsordning
        )
    )
)

fun defaultSøknad(
    søknadId: UUID = UUID.randomUUID(),
    søknadsPeriode: Periode = Periode(LocalDate.now().plusDays(5), LocalDate.now().plusDays(5)),
    søkersIdentitetsnummer: String = "14026223262",
    arbeidstid: Arbeidstid? = Arbeidstid().medArbeidstaker(listOf(defaultArbeidstid(listOf(søknadsPeriode)))),
    tilsynsordning: Tilsynsordning? = defaultTilsynsordning(
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
    arbeidstid: Arbeidstid? = Arbeidstid().medArbeidstaker(listOf(defaultArbeidstid(listOf(søknadsPeriode)))),
    tilsynsordning: Tilsynsordning? = defaultTilsynsordning(
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
): PleiepengerSyktBarn {
    val psb = PleiepengerSyktBarn()
        .medSøknadsperiode(søknadsPeriode)
        .medBarn(Barn().medNorskIdentitetsnummer(NorskIdentitetsnummer.of(barnNorskIdentitetsnummer)))

    arbeidstid?.let { psb.medArbeidstid(arbeidstid) }
    tilsynsordning?.let { psb.medTilsynsordning(tilsynsordning) }

    return psb
}

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

fun <T> assertResultet(faktiskePerioder: Map<Periode, T>, forventedePerioder: Map<Periode, T>) {
    Assertions.assertThat(faktiskePerioder.size).isEqualTo(forventedePerioder.size)
    forventedePerioder.forEach { forventetPeriode: Map.Entry<Periode, T> ->
        println("Forventede perioder: ${forventedePerioder.map { it.key.toString() }} Faktiske perioder: ${faktiskePerioder.map { it.key.toString() }}")
        val data = faktiskePerioder[forventetPeriode.key]
        assertThat(data).isNotNull()
        assertThat(data).isEqualTo(forventetPeriode.value)
    }
}

fun assertResultet(
    faktiskArbeidstaker: Arbeidstaker,
    forventetOrganisasjonsnummer: String,
    forventedePerioder: Map<Periode, ArbeidstidPeriodeInfo>
) {
    assertThat(faktiskArbeidstaker.organisasjonsnummer).isEqualTo(Organisasjonsnummer.of(forventetOrganisasjonsnummer))
    assertResultet(faktiskArbeidstaker.arbeidstidInfo.perioder, forventedePerioder)
}

fun sortertArbeidstakere(resultatYtelse: PleiepengerSyktBarn): List<Arbeidstaker> {
    return resultatYtelse.arbeidstid
        .arbeidstakerList
        .stream()
        .sorted { a: Arbeidstaker, b: Arbeidstaker ->
            a.organisasjonsnummer.verdi.compareTo(b.organisasjonsnummer.verdi)
        }
        .collect(Collectors.toList())
}
