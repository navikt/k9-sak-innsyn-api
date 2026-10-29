package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import no.nav.k9.søknad.ytelse.psb.v1.PleiepengerSyktBarn
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.ArbeidstidInfo
import no.nav.sifinnsynapi.oppslag.OrganisasjonOppslagDTO
import no.nav.sifinnsynapi.soknad.SøknadDTO
import java.time.Duration

/** Det tilgangsreglene trenger fra en sak, uavhengig av k9-format. Orgnr skal ikke logges. */
data class SakForTilgang(
    val søknadsperioder: List<Datoperiode>,
    /** Arbeidsgivere med normalarbeidstid over null. Private arbeidsgivere (uten orgnr) er utelatt. */
    val arbeidsgiverOrgnr: Set<String>,
    /** Minst én periode som selvstendig næringsdrivende med normalarbeidstid over null. */
    val harArbeidstidSomSelvstendigNæringsdrivende: Boolean,
    /** Uten barnets identitetsnummer kan endringen ikke sendes inn. */
    val barnHarIdentitetsnummer: Boolean,
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
            // Søknadsperioder er alltid lukket. Mangler fom eller tom, gir det 5xx.
            Datoperiode(
                fom = requireNotNull(it.fraOgMed) { "Søknadsperiode mangler fraOgMed." },
                tom = requireNotNull(it.tilOgMed) { "Søknadsperiode mangler tilOgMed." },
            )
        },
        arbeidsgiverOrgnr = arbeidstid?.arbeidstakerList.orEmpty()
            .filter { it.arbeidstidInfo.harNormalarbeidstid() }
            .mapNotNull { it.organisasjonsnummer?.verdi }
            .toSet(),
        harArbeidstidSomSelvstendigNæringsdrivende = arbeidstid?.selvstendigNæringsdrivendeArbeidstidInfo
            ?.orElse(null)
            .harNormalarbeidstid(),
        barnHarIdentitetsnummer = !barn.identitetsnummer.isNullOrBlank(),
    )
}

private fun ArbeidstidInfo?.harNormalarbeidstid(): Boolean =
    this?.perioder.orEmpty().values.any { (it.jobberNormaltTimerPerDag ?: Duration.ZERO) > Duration.ZERO }

/** AA-reg gir én rad per ansettelsesperiode. Samler radene per orgnr. */
fun List<OrganisasjonOppslagDTO>.grupperPerArbeidsgiver(): List<ArbeidsgiverForTilgang> =
    groupBy { it.organisasjonsnummer }
        .map { (orgnr, rader) ->
            ArbeidsgiverForTilgang(
                organisasjonsnummer = orgnr,
                ansettelsesperioder = rader.map {
                    // AA-reg har alltid fom. Mangler den, gir det 5xx.
                    Ansettelsesperiode(
                        fom = requireNotNull(it.ansattFom) { "Ansettelsesperiode mangler fom." },
                        tom = it.ansattTom,
                    )
                },
            )
        }
