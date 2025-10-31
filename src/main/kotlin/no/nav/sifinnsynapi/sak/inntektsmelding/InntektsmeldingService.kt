package no.nav.sifinnsynapi.sak.inntektsmelding

import no.nav.k9.innsyn.inntektsmelding.Arbeidsgiver
import no.nav.k9.innsyn.inntektsmelding.Inntektsmelding
import no.nav.k9.innsyn.sak.Saksnummer
import no.nav.k9.søknad.JsonUtils
import no.nav.sifinnsynapi.enhetsregisteret.EnhetsregisterService
import no.nav.sifinnsynapi.oppslag.OppslagsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.stream.Stream
import kotlin.streams.toList

@Service
class InntektsmeldingService(
    private val inntektsmeldingRepository: InntektsmeldingRepository,
    private val oppslagsService: OppslagsService,
    private val enhetsregisterService: EnhetsregisterService,
) {
    private companion object {
        private val logger = LoggerFactory.getLogger(InntektsmeldingService::class.java)
    }

    fun lagreInntektsmelding(inntektsmeldingDAO: InntektsmeldingDAO) {
        inntektsmeldingRepository.save(inntektsmeldingDAO)
    }

    fun hentInntektsmeldingerPåSak(saksnummer: Saksnummer): List<SakInntektsmeldingDTO?> {
        val søkersAktørId =
            (oppslagsService.hentSøker() ?: throw IllegalStateException("Feilet med å hente søkers aktørId.")).aktørId

        return inntektsmeldingRepository.findAllBySøkerAktørIdAndSaksnummerOrderByOppdatertDatoAsc(
            søkerAktørId = søkersAktørId,
            saksnummer = saksnummer.verdi()
        )
            .map { inntektsmeldingDAO -> inntektsmeldingDAO.somSakInntektsmeldingDTO() }
            .toList()

    }

    private fun InntektsmeldingDAO.somSakInntektsmeldingDTO(): SakInntektsmeldingDTO {
        val im = JsonUtils.fromString(inntektsmelding, Inntektsmelding::class.java)

        return SakInntektsmeldingDTO(
            saksnummer = saksnummer,
            journalpostId = journalpostId,
            arbeidsgiver = im.arbeidsgiver.somArbeidsgiverDTO(),
            startDatoPermisjon = im.startDatoPermisjon,
            mottattDato = im.mottattDato,
            inntektBeløp = im.inntektBeløp.verdi,
            innsendingstidspunkt = im.innsendingstidspunkt,
            kildesystem = im.kildesystem,
            erstattetAv = im.erstattetAv.map { it.journalpostId }
        )
    }

    private fun Arbeidsgiver.somArbeidsgiverDTO(): ArbeidsgiverDTO {
        return ArbeidsgiverDTO(
            navn = hentArbeidsgiverNavn(),
            arbeidsgiverOrgnr = arbeidsgiverOrgnr,
        )
    }

    private fun Arbeidsgiver.hentArbeidsgiverNavn(): String? =
        runCatching { enhetsregisterService.hentOrganisasjonsinfo(arbeidsgiverOrgnr) }
            .fold(
                onSuccess = {
                    val sammensattnavn = it.navn?.sammensattnavn
                    if (sammensattnavn.isNullOrBlank()) {
                        logger.warn("Organisasjonsnummer $arbeidsgiverOrgnr hadde ikke navn i Enhetsregisteret. Returnerer null.")
                        null
                    } else
                        sammensattnavn
                },
                onFailure = {
                    logger.warn("Kunne ikke hente organisasjonsnavn for $arbeidsgiverOrgnr. Returnerer null. ${it.message}.")
                    null
                }
            )
}
