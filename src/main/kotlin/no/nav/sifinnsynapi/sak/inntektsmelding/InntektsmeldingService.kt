package no.nav.sifinnsynapi.sak.inntektsmelding

import no.nav.k9.innsyn.inntektsmelding.*
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.*
import no.nav.k9.innsyn.sak.FagsakYtelseType
import no.nav.k9.innsyn.sak.Saksnummer
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.felles.type.Periode
import no.nav.sifinnsynapi.enhetsregisteret.EnhetsregisterService
import no.nav.sifinnsynapi.oppslag.OppslagsService
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

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
            ytelseType = im.fagsakYtelseType.somYtelseType(),
            status = im.status.somInntektsmeldingStatusDto(),
            saksnummer = saksnummer,
            innsendingstidspunkt = im.innsendingstidspunkt,
            kildesystem = im.kildesystem,
            arbeidsgiver = im.arbeidsgiver.somArbeidsgiverDTO(),
            nærRelasjon = im.nærRelasjon,
            journalpostId = journalpostId,
            mottattDato = im.mottattDato,
            inntektBeløp = im.inntektBeløp.verdi,
            innsendingsårsak = im.innsendingsårsak.somInnsendingÅrsak(),
            erstattetAv = im.erstattetAv.map { it.journalpostId },
            graderinger = im.graderinger.map { it.somGradering() },
            naturalYtelser = im.naturalYtelser.map { it.somNaturalYtelseDTO() },
            utsettelsePerioder = im.utsettelsePerioder.map { it.somUtsettelseDTO() },
            startDatoPermisjon = im.startDatoPermisjon,
            oppgittFravær = im.oppgittFravær.map { it.somOppholdDTO() },
            refusjonBeløpPerMnd = im.refusjonBeløpPerMnd?.verdi,
            refusjonOpphører = im.refusjonOpphører,
            inntektsmeldingType = im.inntektsmeldingType?.somInntektsmeldingTypeDTO(),
            endringerRefusjon = im.endringerRefusjon.map { it.somEndringRefusjonDTO() }
        )
    }

    private fun Refusjon.somEndringRefusjonDTO(): EndringRefusjonDTO {
        return EndringRefusjonDTO(
            refusjonBeløpPerMnd = refusjonsbeløpMnd.verdi,
            fom = fom
        )
    }


    private fun InntektsmeldingType.somInntektsmeldingTypeDTO(): InntektsmeldingTypeDTO? {
        return when (this) {
            InntektsmeldingType.ORDINÆR -> InntektsmeldingTypeDTO.ORDINÆR
            InntektsmeldingType.OMSORGSPENGER_REFUSJON -> InntektsmeldingTypeDTO.OMSORGSPENGER_REFUSJON
            InntektsmeldingType.ARBEIDSGIVERINITIERT_NYANSATT -> InntektsmeldingTypeDTO.ARBEIDSGIVERINITIERT_NYANSATT
            InntektsmeldingType.ARBEIDSGIVERINITIERT_UREGISTRERT -> InntektsmeldingTypeDTO.ARBEIDSGIVERINITIERT_UREGISTRERT
        }
    }

    private fun PeriodeAndel.somOppholdDTO(): OppholdDTO {
        return OppholdDTO(
            periode = periode.somPeriodeDTO(),
            varighetPerDag = varighetPerDag
        )
    }

    private fun UtsettelsePeriode.somUtsettelseDTO(): UtsettelseDTO {
        return UtsettelseDTO(
            periode = periode.somPeriodeDTO(),
            årsak = årsak.somUtsettelseÅrsakDTO()
        )
    }

    private fun UtsettelseÅrsak.somUtsettelseÅrsakDTO(): UtsettelseÅrsakDTO {
        return when (this) {
            UtsettelseÅrsak.ARBEID -> UtsettelseÅrsakDTO.ARBEID
            UtsettelseÅrsak.FERIE -> UtsettelseÅrsakDTO.FERIE
            UtsettelseÅrsak.SYKDOM -> UtsettelseÅrsakDTO.SYKDOM
            UtsettelseÅrsak.INSTITUSJON_SØKER -> UtsettelseÅrsakDTO.INSTITUSJON_SØKER
            UtsettelseÅrsak.INSTITUSJON_BARN -> UtsettelseÅrsakDTO.INSTITUSJON_BARN
            UtsettelseÅrsak.UDEFINERT -> UtsettelseÅrsakDTO.UDEFINERT
        }
    }


    private fun NaturalYtelse.somNaturalYtelseDTO(): NaturalYtelseDTO {
        return NaturalYtelseDTO(
            periode = periode.somPeriodeDTO(),
            beløpPerMnd = beloepPerMnd.verdi,
            type = type.somNaturalYtelseTypeDTO()
        )
    }

    private fun NaturalYtelseType.somNaturalYtelseTypeDTO(): NaturalYtelseTypeDTO {
        return when (this) {
            ELEKTRISK_KOMMUNIKASJON -> NaturalYtelseTypeDTO.ELEKTRISK_KOMMUNIKASJON
            AKSJER_GRUNNFONDSBEVIS_TIL_UNDERKURS -> NaturalYtelseTypeDTO.AKSJER_GRUNNFONDSBEVIS_TIL_UNDERKURS
            LOSJI -> NaturalYtelseTypeDTO.LOSJI
            KOST_DØGN -> NaturalYtelseTypeDTO.KOST_DØGN
            BESØKSREISER_HJEMMET_ANNET -> NaturalYtelseTypeDTO.BESØKSREISER_HJEMMET_ANNET
            KOSTBESPARELSE_I_HJEMMET -> NaturalYtelseTypeDTO.KOSTBESPARELSE_I_HJEMMET
            RENTEFORDEL_LÅN -> NaturalYtelseTypeDTO.RENTEFORDEL_LÅN
            BIL -> NaturalYtelseTypeDTO.BIL
            KOST_DAGER -> NaturalYtelseTypeDTO.KOST_DAGER
            BOLIG -> NaturalYtelseTypeDTO.BOLIG
            SKATTEPLIKTIG_DEL_FORSIKRINGER -> NaturalYtelseTypeDTO.SKATTEPLIKTIG_DEL_FORSIKRINGER
            FRI_TRANSPORT -> NaturalYtelseTypeDTO.FRI_TRANSPORT
            OPSJONER -> NaturalYtelseTypeDTO.OPSJONER
            TILSKUDD_BARNEHAGEPLASS -> NaturalYtelseTypeDTO.TILSKUDD_BARNEHAGEPLASS
            ANNET -> NaturalYtelseTypeDTO.ANNET
            BEDRIFTSBARNEHAGEPLASS -> NaturalYtelseTypeDTO.BEDRIFTSBARNEHAGEPLASS
            YRKEBIL_TJENESTLIGBEHOV_KILOMETER -> NaturalYtelseTypeDTO.YRKEBIL_TJENESTLIGBEHOV_KILOMETER
            YRKEBIL_TJENESTLIGBEHOV_LISTEPRIS -> NaturalYtelseTypeDTO.YRKEBIL_TJENESTLIGBEHOV_LISTEPRIS
            INNBETALING_TIL_UTENLANDSK_PENSJONSORDNING -> NaturalYtelseTypeDTO.INNBETALING_TIL_UTENLANDSK_PENSJONSORDNING
            UDEFINERT -> NaturalYtelseTypeDTO.UDEFINERT
        }
    }

    private fun InntektsmeldingInnsendingsårsak.somInnsendingÅrsak(): InnsendingsårsakDTO {
        return when (this) {
            InntektsmeldingInnsendingsårsak.NY -> InnsendingsårsakDTO.NY
            InntektsmeldingInnsendingsårsak.ENDRING -> InnsendingsårsakDTO.ENDRING
            InntektsmeldingInnsendingsårsak.UDEFINERT -> InnsendingsårsakDTO.UDEFINERT
        }
    }


    private fun Gradering.somGradering(): GraderingDTO {
        return GraderingDTO(
            arbeidstidProsent = arbeidstidProsent.verdi,
            periode = periode.somPeriodeDTO()
        )

    }

    private fun Periode.somPeriodeDTO(): PeriodeDTO {
        return PeriodeDTO(
            fom = fraOgMed,
            tom = tilOgMed
        )
    }

    private fun FagsakYtelseType.somYtelseType(): YtekseTypeDTO {
        return when (this) {
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN -> YtekseTypeDTO.PLEIEPENGER_SYKT_BARN
            FagsakYtelseType.PLEIEPENGER_NÆRSTÅENDE -> YtekseTypeDTO.PLEIEPENGER_NÆRSTÅENDE
            FagsakYtelseType.OMSORGSPENGER_KS -> YtekseTypeDTO.OMSORGSPENGER_KS
            FagsakYtelseType.OMSORGSPENGER_MA -> YtekseTypeDTO.OMSORGSPENGER_MA
            FagsakYtelseType.OMSORGSPENGER_AO -> YtekseTypeDTO.OMSORGSPENGER_AO
            FagsakYtelseType.OPPLÆRINGSPENGER -> YtekseTypeDTO.OPPLÆRINGSPENGER
            FagsakYtelseType.OMSORGSPENGER -> YtekseTypeDTO.OPPLÆRINGSPENGER
        }
    }

    private fun InntektsmeldingStatus.somInntektsmeldingStatusDto(): InntektsmeldingStatusDto = when (this) {
        InntektsmeldingStatus.I_BRUK -> InntektsmeldingStatusDto.I_BRUK
        InntektsmeldingStatus.MANGLER_DATO -> InntektsmeldingStatusDto.MANGLER_DATO
        InntektsmeldingStatus.IKKE_RELEVANT -> InntektsmeldingStatusDto.IKKE_RELEVANT
        InntektsmeldingStatus.ERSTATTET_AV_NYERE -> InntektsmeldingStatusDto.ERSTATTET_AV_NYERE
    }

    private fun Arbeidsgiver.somArbeidsgiverDTO(): ArbeidsgiverDTO {
        return ArbeidsgiverDTO(
            organisasjon = arbeidsgiverOrgnr?.let {
                ArbeidsgiverOrganisasjonDTO(
                    navn = hentArbeidsgiverNavn(),
                    organisasjonsnummer = it
                )
            },
            privat = arbeidsgiverAktørId?.let {
                throw NotImplementedError("Privat arbeidsgiver i inntektsmeldingen er ikke implementert enda.")
            }
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


