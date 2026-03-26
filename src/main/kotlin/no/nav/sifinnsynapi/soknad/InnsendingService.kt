package no.nav.sifinnsynapi.soknad

import no.nav.k9.innsyn.Søknadsammenslåer
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.personopplysninger.Barn
import no.nav.k9.søknad.ytelse.Ytelse
import no.nav.k9.søknad.ytelse.psb.v1.PleiepengerSyktBarn
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.LegacyInnsynApiService
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.LegacySøknadstype
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.NotSupportedArbeidsgiverMeldingException
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.utils.PSBJsonUtils
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.utils.PSBJsonUtils.finnOrganisasjon
import no.nav.sifinnsynapi.legacy.legacyinnsynapi.utils.PSBJsonUtils.tilArbeidstakernavn
import no.nav.sifinnsynapi.omsorg.OmsorgService
import no.nav.sifinnsynapi.oppslag.BarnOppslagDTO
import no.nav.sifinnsynapi.oppslag.HentBarnForespørsel
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.pdf.ArbeidsgiverMeldingNavNoPDFGenerator
import no.nav.sifinnsynapi.pdf.ArbeidsgiverMeldingPDFGenerator
import no.nav.sifinnsynapi.pdf.PleiepengerArbeidsgiverMelding
import no.nav.sifinnsynapi.pdf.SøknadsPeriode
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.*


@Service
class InnsendingService(
    private val søknadRepository: SøknadRepository,
    private val omsorgService: OmsorgService,
    private val oppslagsService: OppslagsService,
    private val legacyInnsynApiService: LegacyInnsynApiService,
    private val ettersendelseRepository: EttersendelseRepository,
    private val arbeidsgiverMeldingPDFGenerator: ArbeidsgiverMeldingPDFGenerator,
    private val arbeidsgiverMeldingNavNoPDFGenerator: ArbeidsgiverMeldingNavNoPDFGenerator,
    @Value("\${no.nav.inntektsmelding.ny-im-aktivert}") private val erNyImAktivert: Boolean
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(InnsendingService::class.java)
    }

    fun hentSøknad(journalpostId: String): PsbSøknadDAO? {
        return søknadRepository.findById(journalpostId).orElse(null)
    }

    @Transactional(readOnly = true)
    fun slåSammenSøknadsopplysningerPerBarn(): List<SøknadDTO> {
        val søkersAktørId =
            (oppslagsService.hentSøker()
                ?: throw IllegalStateException("Feilet med å hente søkers aktørId.")).aktørId

        val pleietrengendeSøkerHarOmsorgFor = omsorgService.hentPleietrengendeSøkerHarOmsorgFor(søkersAktørId)
        logger.info("Fant {} pleietrengende søker har omsorgen for.", pleietrengendeSøkerHarOmsorgFor.size)

        val søknaderPerPleietrengende = søknadRepository.findAllBySøkerAktørIdOrderByOppdatertDatoAsc(søkersAktørId)
            .groupBy { it.pleietrengendeAktørId }

        val allePleietrengendeAktørIder = søknaderPerPleietrengende.keys.toList()
        val barnOppslagDTOS: List<BarnOppslagDTO> = if (allePleietrengendeAktørIder.isNotEmpty()) {
            oppslagsService.systemoppslagBarn(HentBarnForespørsel(identer = allePleietrengendeAktørIder))
        } else {
            emptyList()
        }

        return søknaderPerPleietrengende
            .mapNotNull { (pleietrengendeAktørId, psbSøknader) ->
                slåSammenSøknaderOgMapTilDTO(pleietrengendeAktørId, psbSøknader, barnOppslagDTOS, pleietrengendeSøkerHarOmsorgFor)
            }
    }

    private fun slåSammenSøknaderOgMapTilDTO(
        pleietrengendeAktørId: String,
        psbSøknader: List<PsbSøknadDAO>,
        barnOppslagDTOS: List<BarnOppslagDTO>,
        pleietrengendeSøkerHarOmsorgFor: List<String>,
    ): SøknadDTO? {
        // Hvis pleietrengende ikke finnes i systemoppslag, filtrer ut søknaden
        val barnOppslag = barnOppslagDTOS.firstOrNull { it.aktørId == pleietrengendeAktørId }
            ?: return null

        val sammenslåttSøknad = slåSammenPsbSøknader(psbSøknader) ?: return null

        // Hvis pleietrengende finnes i systemoppslag men søker ikke har omsorg, anonymiser
        val søkerHarOmsorg = pleietrengendeSøkerHarOmsorgFor.contains(pleietrengendeAktørId)
        if (!søkerHarOmsorg) {
            anonymiserBarnIYtelse(sammenslåttSøknad)
            return sammenslåttSøknad.somSøknadDTO(anonymisertBarn(pleietrengendeAktørId))
        }

        return sammenslåttSøknad.somSøknadDTO(barnOppslag)
    }

    private fun slåSammenPsbSøknader(psbSøknader: List<PsbSøknadDAO>): Søknad? {
        return psbSøknader
            .map { JsonUtils.fromString(it.søknad, Søknad::class.java) }
            .filter { it.getYtelse<Ytelse>() is PleiepengerSyktBarn }
            .reduceOrNull(Søknadsammenslåer::slåSammen)
    }

    private fun anonymiserBarnIYtelse(søknad: Søknad) {
        søknad.getYtelse<PleiepengerSyktBarn>().medBarn(Barn())
    }

    @Transactional(readOnly = true)
    fun slåSammenSøknaderFor(
        søkersAktørId: String,
        pleietrengendeAktørId: String,
    ): Søknad? {
        return søknadRepository.findAllBySøkerAktørIdAndPleietrengendeAktørIdOrderByOppdatertDatoAsc(søkersAktørId, pleietrengendeAktørId)
            .map { psbSøknadDAO: PsbSøknadDAO ->
                JsonUtils.fromString(psbSøknadDAO.søknad, Søknad::class.java)
            }
            .reduceOrNull(Søknadsammenslåer::slåSammen)
    }

    fun lagreSøknad(søknad: PsbSøknadDAO): PsbSøknadDAO = søknadRepository.save(søknad)

    @Transactional
    fun trekkSøknad(journalpostId: String): Boolean {
        søknadRepository.deleteById(journalpostId)
        return !søknadRepository.existsById(journalpostId)
    }

    private fun Søknad.somSøknadDTO(barn: BarnOppslagDTO, alleSøknader: List<Søknad>? = null): SøknadDTO {
        return SøknadDTO(
            barn = barn,
            søknad = this,
            søknader = alleSøknader
        )
    }

    private fun anonymisertBarn(pleietrengendeAktørId: String): BarnOppslagDTO {
        return BarnOppslagDTO(
            aktørId = pleietrengendeAktørId,
            fødselsdato = LocalDate.EPOCH,
            fornavn = "",
            mellomnavn = null,
            etternavn = "",
            identitetsnummer = null
        )
    }



    fun hentArbeidsgiverMeldingFil(søknadId: UUID, organisasjonsnummer: String): ByteArray {

        val søknad = legacyInnsynApiService.hentLegacySøknad(søknadId.toString())

        return when (søknad.søknadstype) {
            LegacySøknadstype.PP_SYKT_BARN -> {
                val pleiepengesøknadJson = JSONObject(søknad.søknad)
                val funnetOrg: JSONObject = pleiepengesøknadJson.finnOrganisasjon(søknadId.toString(), organisasjonsnummer)

                logger.info("Skal generere arbeidsgivermelding. erNyImAktivert=$erNyImAktivert")

                if (erNyImAktivert) {
                    logger.info("Ny inntektsmelding er aktivert, genererer PDF med nytt template.")
                    arbeidsgiverMeldingNavNoPDFGenerator.genererPDF(
                        pleiepengesøknadJson.tilPleiepengerAreidsgivermelding(
                            funnetOrg
                        )
                    )
                } else {
                    arbeidsgiverMeldingPDFGenerator.genererPDF(
                        pleiepengesøknadJson.tilPleiepengerAreidsgivermelding(
                            funnetOrg
                        )
                    )
                }
            }

            else -> throw NotSupportedArbeidsgiverMeldingException(søknadId.toString(), søknad.søknadstype)
        }
    }

    fun JSONObject.tilPleiepengerAreidsgivermelding(funnetOrg: JSONObject) = PleiepengerArbeidsgiverMelding(
        søknadsperiode = SøknadsPeriode(
            fraOgMed = LocalDate.parse(getString(PSBJsonUtils.FRA_OG_MED)),
            tilOgMed = LocalDate.parse(getString(PSBJsonUtils.TIL_OG_MED)),
        ),
        arbeidsgivernavn = funnetOrg.optString(PSBJsonUtils.ORGANISASJONSNAVN, null),
        arbeidstakernavn = getJSONObject(PSBJsonUtils.SØKER).tilArbeidstakernavn()
    )

    fun lagreEttersendelse(ettersendelse: EttersendelseDAO) {
        ettersendelseRepository.save(ettersendelse)
    }

    fun hentEttersendelse(journalpostId: String): EttersendelseDAO? {
        return ettersendelseRepository.finnForJournalpost(journalpostId).orElse(null)
    }
}

