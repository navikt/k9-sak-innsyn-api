package no.nav.sifinnsynapi.sak.inntektsmelding

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class SakInntektsmeldingDTO(
    val status: InntektsmeldingStatusDto,
    val saksnummer: String,
    val journalpostId: String,
    val arbeidsgiver: ArbeidsgiverDTO,
    val startDatoPermisjon: LocalDate?,
    val mottattDato: LocalDate,
    val inntektBeløp: BigDecimal,
    val innsendingstidspunkt: LocalDateTime,
    val kildesystem: String?,
    val erstattetAv: List<String>,
)
