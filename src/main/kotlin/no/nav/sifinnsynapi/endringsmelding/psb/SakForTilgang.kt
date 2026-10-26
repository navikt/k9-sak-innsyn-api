package no.nav.sifinnsynapi.endringsmelding.psb

import no.nav.k9.søknad.ytelse.psb.v1.PleiepengerSyktBarn
import no.nav.sifinnsynapi.oppslag.OrganisasjonOppslagDTO
import no.nav.sifinnsynapi.soknad.SøknadDTO

/**
 * Det tilgangsreglene trenger fra en sak, og ikke noe mer.
 *
 * Reglene jobber på denne modellen i stedet for direkte på k9-format. Det holder reglene uavhengige av
 * k9-format og gjør testdata enkle. Modellen inneholder ingen personopplysninger, men orgnr skal heller
 * ikke logges.
 */
data class SakForTilgang(
    val søknadsperioder: List<Datoperiode>,
    /**
     * Orgnr for arbeidstakerne i saken. Arbeidstakere uten orgnr (privat arbeidsgiver, bare
     * norskIdentitetsnummer) er utelatt, og kan dermed aldri matche en arbeidsgiver fra AA-reg.
     */
    val arbeidsgiverOrgnr: Set<String>,
    /** True når saken har minst én periode som selvstendig næringsdrivende, uansett timetall. */
    val harSelvstendigNæringsdrivendePerioder: Boolean,
)

/** En arbeidsgiver fra AA-reg med alle ansettelsesperiodene for samme orgnr samlet. */
data class ArbeidsgiverForTilgang(
    val organisasjonsnummer: String,
    val ansettelsesperioder: List<Ansettelsesperiode>,
)

fun SøknadDTO.tilSakForTilgang(): SakForTilgang {
    val ytelse = søknad.getYtelse<PleiepengerSyktBarn>()
    val arbeidstid = ytelse.arbeidstid

    return SakForTilgang(
        søknadsperioder = ytelse.søknadsperiodeList.orEmpty().map {
            // Søknadsperioder i en PSB-sak er alltid lukket. Mangler fom eller tom, er saken ugyldig og
            // oppslaget feiler med 5xx.
            Datoperiode(
                fom = requireNotNull(it.fraOgMed) { "Søknadsperiode mangler fraOgMed." },
                tom = requireNotNull(it.tilOgMed) { "Søknadsperiode mangler tilOgMed." },
            )
        },
        arbeidsgiverOrgnr = arbeidstid?.arbeidstakerList.orEmpty()
            .mapNotNull { it.organisasjonsnummer?.verdi }
            .toSet(),
        harSelvstendigNæringsdrivendePerioder = arbeidstid?.selvstendigNæringsdrivendeArbeidstidInfo
            ?.orElse(null)
            ?.perioder
            .orEmpty()
            .isNotEmpty(),
    )
}

/**
 * AA-reg returnerer én rad per ansettelsesforhold når inkluderAlleAnsettelsesperioder = true.
 * Her samles radene per orgnr, slik at hver arbeidsgiver får en liste med ansettelsesperioder.
 */
fun List<OrganisasjonOppslagDTO>.grupperPerArbeidsgiver(): List<ArbeidsgiverForTilgang> =
    groupBy { it.organisasjonsnummer }
        .map { (orgnr, rader) ->
            ArbeidsgiverForTilgang(
                organisasjonsnummer = orgnr,
                ansettelsesperioder = rader.map { Ansettelsesperiode(fom = it.ansattFom, tom = it.ansattTom) },
            )
        }
