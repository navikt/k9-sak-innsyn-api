package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import net.logstash.logback.argument.StructuredArguments.entries
import no.nav.sifinnsynapi.oppslag.HentIdenterForespørsel
import no.nav.sifinnsynapi.oppslag.IdentGruppe
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.oppslag.OrganisasjonOppslagDTO
import no.nav.sifinnsynapi.soknad.InnsendingService
import no.nav.sifinnsynapi.soknad.PsbSøknadDAO
import no.nav.sifinnsynapi.soknad.SøknadDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

/**
 * Avgjør om søkeren kan bruke endringsmeldingen for pleiepenger, og gir årsakene når svaret er nei.
 *
 * Flyt: hent saker → [vurderSaker] → hent arbeidsgivere for oppslagsperioden → [vurderArbeidsforhold].
 *
 * Feil fra oppslagene gir 5xx, aldri tilgang. Se [OppslagsService.hentArbeidsgivere].
 */
@Service
class EndringsmeldingTilgangService(
    private val innsendingService: InnsendingService,
    private val oppslagsService: OppslagsService,
    private val clock: Clock,
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(EndringsmeldingTilgangService::class.java)
    }

    /** For innlogget bruker. */
    fun vurderTilgang(): TilgangResultat = vurderTilgang(
        kaltAv = "bruker",
        søknader = innsendingService.slåSammenSøknadsopplysningerPerBarn(),
        hentArbeidsgivere = { oppslagsService.hentArbeidsgivere(fraOgMed = it.fom, tilOgMed = it.tom) },
    )

    /**
     * For saksbehandler. Kalleren har ansvar for at saksbehandleren har tilgang til søkeren
     * og alle pleietrengende i [søknader].
     */
    fun vurderTilgang(søkerAktørId: String, søknader: List<PsbSøknadDAO>): TilgangResultat = vurderTilgang(
        kaltAv = "saksbehandler",
        søknader = innsendingService.slåSammenSøknadsopplysningerPerBarn(søkerAktørId, søknader),
        hentArbeidsgivere = {
            oppslagsService.systemoppslagArbeidsgivere(
                fødselsnummer = hentFødselsnummer(søkerAktørId),
                fraOgMed = it.fom,
                tilOgMed = it.tom,
            )
        },
    )

    private fun vurderTilgang(
        kaltAv: String,
        søknader: List<SøknadDTO>,
        hentArbeidsgivere: (Datoperiode) -> List<OrganisasjonOppslagDTO>,
    ): TilgangResultat {
        val endringsperiode = tillattEndringsperiode(LocalDate.now(clock))
        val saker = søknader.map { it.tilSakForTilgang() }

        val resultat = when (val sakVurdering = vurderSaker(saker, endringsperiode)) {
            is SakVurdering.IngenTilgang -> TilgangResultat.ingenTilgang(listOf(sakVurdering.årsak))
            is SakVurdering.Tilgang -> {
                val arbeidsgivere = hentArbeidsgivere(sakVurdering.oppslagsperiode).grupperPerArbeidsgiver()
                val årsaker = vurderArbeidsforhold(sakVurdering.sak, arbeidsgivere, endringsperiode)

                if (årsaker.isEmpty())
                    TilgangResultat.tilgang(endringsperiode)
                else
                    TilgangResultat.ingenTilgang(årsaker)
            }
        }

        // Logg resultat og bruk første årsak hvis !kanBruke
        val felter = buildMap<String, Any> {
            put("hendelse", "endringsmelding_tilgang_vurdert")
            put("kalt_av", kaltAv)
            put("kan_bruke", resultat.kanBruke)
            resultat.årsaker.firstOrNull()?.let { put("forste_aarsak", it.kode) }
        }
        logger.info("Vurderte tilgang til endringsmelding. {}", entries(felter))
        return resultat
    }

    // Aareg krever fødselsnummer. Vi slår det opp selv i stedet for å stole på at k9-sak sender et som hører til aktørId-en.
    private fun hentFødselsnummer(aktørId: String): String =
        oppslagsService.hentIdenter(
            HentIdenterForespørsel(identer = listOf(aktørId), identGrupper = listOf(IdentGruppe.FOLKEREGISTERIDENT))
        )
            .singleOrNull()?.identer
            ?.firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }?.ident
            ?: throw IllegalStateException("Fant ikke fødselsnummer for søkeren.")
}
