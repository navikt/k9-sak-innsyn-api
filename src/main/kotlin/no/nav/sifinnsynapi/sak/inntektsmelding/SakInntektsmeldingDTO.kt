package no.nav.sifinnsynapi.sak.inntektsmelding

import no.nav.sifinnsynapi.sak.inntektsmelding.typer.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class SakInntektsmeldingDTO(
    val ytelseType: YtekseTypeDTO,
    val status: InntektsmeldingStatusDto,
    val saksnummer: String,
    val innsendingstidspunkt: LocalDateTime,
    val kildesystem: String,
    val arbeidsgiver: ArbeidsgiverDTO,
    val nærRelasjon: Boolean,
    val journalpostId: String,
    val mottattDato: LocalDate,
    val inntektBeløp: BigDecimal,
    val innsendingsårsak: InnsendingsårsakDTO,
    val erstattetAv: List<String>,
    val graderinger: List<GraderingDTO>?,
    val naturalYtelser: List<NaturalYtelseDTO>?,
    val utsettelsePerioder: List<UtsettelseDTO>?,
    val startDatoPermisjon: LocalDate?,
    val oppgittFravær: List<OppholdDTO>?,
    val refusjonBeløpPerMnd: BigDecimal?,
    val refusjonOpphører: LocalDate?,
    val inntektsmeldingType: InntektsmeldingTypeDTO?,
    val endringerRefusjon: List<EndringRefusjonDTO>?,
)
