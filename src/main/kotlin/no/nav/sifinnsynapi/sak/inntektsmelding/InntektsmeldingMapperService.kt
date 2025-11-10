package no.nav.sifinnsynapi.sak.inntektsmelding

import no.nav.k9.innsyn.inntektsmelding.Arbeidsgiver
import no.nav.k9.innsyn.inntektsmelding.Gradering
import no.nav.k9.innsyn.inntektsmelding.Inntektsmelding
import no.nav.k9.innsyn.inntektsmelding.InntektsmeldingInnsendingsårsak
import no.nav.k9.innsyn.inntektsmelding.InntektsmeldingStatus
import no.nav.k9.innsyn.inntektsmelding.InntektsmeldingType
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelse
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.AKSJER_GRUNNFONDSBEVIS_TIL_UNDERKURS
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.ANNET
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.BEDRIFTSBARNEHAGEPLASS
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.BESØKSREISER_HJEMMET_ANNET
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.BIL
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.BOLIG
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.ELEKTRISK_KOMMUNIKASJON
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.FRI_TRANSPORT
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.INNBETALING_TIL_UTENLANDSK_PENSJONSORDNING
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.KOSTBESPARELSE_I_HJEMMET
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.KOST_DAGER
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.KOST_DØGN
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.LOSJI
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.OPSJONER
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.RENTEFORDEL_LÅN
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.SKATTEPLIKTIG_DEL_FORSIKRINGER
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.TILSKUDD_BARNEHAGEPLASS
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.UDEFINERT
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.YRKEBIL_TJENESTLIGBEHOV_KILOMETER
import no.nav.k9.innsyn.inntektsmelding.NaturalYtelseType.YRKEBIL_TJENESTLIGBEHOV_LISTEPRIS
import no.nav.k9.innsyn.inntektsmelding.PeriodeAndel
import no.nav.k9.innsyn.inntektsmelding.Refusjon
import no.nav.k9.innsyn.inntektsmelding.UtsettelsePeriode
import no.nav.k9.innsyn.inntektsmelding.UtsettelseÅrsak
import no.nav.k9.innsyn.sak.FagsakYtelseType
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.felles.type.Periode
import no.nav.sifinnsynapi.enhetsregisteret.EnhetsregisterService
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.ArbeidsgiverDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.ArbeidsgiverOrganisasjonDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.EndringRefusjonDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.GraderingDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.InnsendingsårsakDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.InntektsmeldingStatusDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.InntektsmeldingTypeDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.NaturalYtelseDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.NaturalYtelseTypeDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.OppholdDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.PeriodeDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.RefusjonDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.SakInntektsmeldingDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.UtsettelseDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.UtsettelseÅrsakDTO
import no.nav.sifinnsynapi.sak.inntektsmelding.typer.YtekseTypeDTO
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class InntektsmeldingMapperService(private val enhetsregisterService: EnhetsregisterService) {
    private companion object {
        private val logger = LoggerFactory.getLogger(InntektsmeldingMapperService::class.java)
    }

    fun mapTilInntektsmeldingDTO(inntektsmeldingDAO: InntektsmeldingDAO): SakInntektsmeldingDTO? {
        val im = JsonUtils.fromString(inntektsmeldingDAO.inntektsmelding, Inntektsmelding::class.java)

        if (im.arbeidsgiver.arbeidsgiverAktørId != null) {
            logger.warn("Privat arbeidsgiver i inntektsmeldingen er ikke implementert enda, filtrerer den bort. journalpostId=${inntektsmeldingDAO.journalpostId}")
            return null
        }

        return SakInntektsmeldingDTO(
            ytelseType = im.fagsakYtelseType.somYtelseType(),
            status = im.status.somInntektsmeldingStatusDto(),
            saksnummer = inntektsmeldingDAO.saksnummer,
            innsendingstidspunkt = im.innsendingstidspunkt,
            kildesystem = im.kildesystem,
            arbeidsgiver = im.arbeidsgiver.somArbeidsgiverDTO(),
            nærRelasjon = im.nærRelasjon,
            journalpostId = inntektsmeldingDAO.journalpostId,
            mottattDato = im.mottattDato,
            inntektBeløp = im.inntektBeløp.verdi,
            innsendingsårsak = im.innsendingsårsak.somInnsendingÅrsak(),
            erstattetAv = im.erstattetAv.map { it.journalpostId },
            graderinger = im.graderinger.map { it.somGradering() },
            naturalYtelser = im.naturalYtelser.map { it.somNaturalYtelseDTO() },
            utsettelsePerioder = im.utsettelsePerioder.map { it.somUtsettelseDTO() },
            startDatoPermisjon = im.startDatoPermisjon,
            oppgittFravær = im.oppgittFravær.map { it.somOppholdDTO() },
            refusjon = im.somRefusjonDTO(),
            inntektsmeldingType = im.inntektsmeldingType?.somInntektsmeldingTypeDTO(),
            endringerRefusjon = im.endringerRefusjon.map { it.somEndringRefusjonDTO() }
        )
    }

    private fun Inntektsmelding.somRefusjonDTO(): RefusjonDTO? {
        return refusjonBeløpPerMnd?.let {
            RefusjonDTO(
                refusjonBeløpPerMnd = it.verdi,
                refusjonOpphører = refusjonOpphører
            )
        }
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

    private fun InntektsmeldingStatus.somInntektsmeldingStatusDto(): InntektsmeldingStatusDTO = when (this) {
        InntektsmeldingStatus.I_BRUK -> InntektsmeldingStatusDTO.I_BRUK
        InntektsmeldingStatus.MANGLER_DATO -> InntektsmeldingStatusDTO.MANGLER_DATO
        InntektsmeldingStatus.IKKE_RELEVANT -> InntektsmeldingStatusDTO.IKKE_RELEVANT
        InntektsmeldingStatus.ERSTATTET_AV_NYERE -> InntektsmeldingStatusDTO.ERSTATTET_AV_NYERE
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
