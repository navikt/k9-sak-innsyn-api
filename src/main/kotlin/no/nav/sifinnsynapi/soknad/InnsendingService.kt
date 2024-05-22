package no.nav.sifinnsynapi.soknad

import no.nav.k9.innsyn.Søknadsammenslåer
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
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
import no.nav.sifinnsynapi.pdf.ArbeidsgiverMeldingPDFGenerator
import no.nav.sifinnsynapi.pdf.PleiepengerArbeidsgiverMelding
import no.nav.sifinnsynapi.pdf.SøknadsPeriode
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.*
import java.util.stream.Stream


@Service
class InnsendingService(
    private val søknadRepository: SøknadRepository,
    private val omsorgService: OmsorgService,
    private val oppslagsService: OppslagsService,
    private val legacyInnsynApiService: LegacyInnsynApiService,
    private val arbeidsgiverMeldingPDFGenerator: ArbeidsgiverMeldingPDFGenerator,
    private val ettersendelseRepository: EttersendelseRepository
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
        if (pleietrengendeSøkerHarOmsorgFor.isEmpty()) {
            logger.info("Fant ingen pleietrengende søker har omsorgen for.")
            return listOf()
        }

        val barnOppslagDTOS: List<BarnOppslagDTO> = oppslagsService.systemoppslagBarn(HentBarnForespørsel(identer = pleietrengendeSøkerHarOmsorgFor))
        if (barnOppslagDTOS.isEmpty()) {
            return emptyList()
        }
        logger.info("Fant {} pleietrengende søker har omsorgen for.", pleietrengendeSøkerHarOmsorgFor.size)

        return pleietrengendeSøkerHarOmsorgFor
            .mapNotNull { pleietrengendeAktørId ->
                val barn = barnOppslagDTOS.firstOrNull { it.aktørId == pleietrengendeAktørId }
                if (barn != null) {
                    slåSammenSøknaderFor(søkersAktørId, pleietrengendeAktørId)?.somSøknadDTO(barn)
                } else {
                    logger.info("PleietrengedeAktørId matchet ikke med aktørId på barn fra oppslag.")
                    null
                }
            }
    }

    @Transactional(readOnly = true)
    fun slåSammenSøknaderFor(
        søkersAktørId: String,
        pleietrengendeAktørId: String,
    ): Søknad? {
        return søknadRepository.findAllByPleietrengendeAktørIdOrderByOppdatertDatoAsc(pleietrengendeAktørId)
            .use { søknadStream: Stream<PsbSøknadDAO> ->
                søknadStream.map { psbSøknadDAO: PsbSøknadDAO ->
                    psbSøknadDAO.kunPleietrengendeDataFraAndreSøkere(søkersAktørId)
                }
                    .reduce(Søknadsammenslåer::slåSammen)
                    .orElse(null)
            }
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

    private fun PsbSøknadDAO.kunPleietrengendeDataFraAndreSøkere(søkerAktørId: String): Søknad {
        val søknad = JsonUtils.fromString(this.søknad, Søknad::class.java)
        return when (this.søkerAktørId) {
            søkerAktørId -> søknad
            else -> Søknadsammenslåer.kunPleietrengendedata(søknad)
        }
    }


    fun hentArbeidsgiverMeldingFil(søknadId: UUID, organisasjonsnummer: String): ByteArray {

        val søknad = legacyInnsynApiService.hentLegacySøknad(søknadId.toString())

        return when (søknad.søknadstype) {
            LegacySøknadstype.PP_SYKT_BARN -> {
                val pleiepengesøknadJson = JSONObject(søknad.søknad)
                val funnetOrg: JSONObject = pleiepengesøknadJson.finnOrganisasjon(søknadId.toString(), organisasjonsnummer)

                arbeidsgiverMeldingPDFGenerator.genererPDF(
                    pleiepengesøknadJson.tilPleiepengerAreidsgivermelding(
                        funnetOrg
                    )
                )
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

