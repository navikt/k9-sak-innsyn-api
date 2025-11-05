package no.nav.sifinnsynapi.sak.inntektsmelding

import no.nav.k9.innsyn.sak.Saksnummer
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.SakInntektsmeldingDTO
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class InntektsmeldingService(
    private val inntektsmeldingRepository: InntektsmeldingRepository,
    private val oppslagsService: OppslagsService,
    private val inntektsmeldingMapperService: InntektsmeldingMapperService,
) {
    private companion object {
        private val logger = LoggerFactory.getLogger(InntektsmeldingService::class.java)
    }

    fun lagreInntektsmelding(inntektsmeldingDAO: InntektsmeldingDAO) {
        inntektsmeldingRepository.save(inntektsmeldingDAO)
    }

    fun hentInntektsmeldingerPåSak(saksnummer: Saksnummer): List<SakInntektsmeldingDTO?> {
        logger.info("Henter inntektsmeldinger registrert på sak ${saksnummer.verdi}")
        val søkersAktørId = (oppslagsService.hentSøker()
            ?: throw IllegalStateException("Feilet med å hente søkers aktørId.")).aktørId

        return inntektsmeldingRepository
            .findAllBySøkerAktørIdAndSaksnummerOrderByOppdatertDatoAsc(
                søkerAktørId = søkersAktørId,
                saksnummer = saksnummer.verdi()
            )
            .mapNotNull { inntektsmeldingDAO -> inntektsmeldingMapperService.mapTilInntektsmeldingDTO(inntektsmeldingDAO) }
    }
}

