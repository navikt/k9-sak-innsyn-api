package no.nav.sifinnsynapi.sak

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.k9.innsyn.sak.Aksjonspunkt
import no.nav.k9.innsyn.sak.BehandlingStatus
import no.nav.k9.innsyn.sak.FagsakYtelseType
import no.nav.k9.innsyn.sak.Saksnummer
import no.nav.k9.søknad.Innsending
import no.nav.k9.søknad.felles.DtoKonstanter
import no.nav.sifinnsynapi.oppslag.Organisasjon
import java.net.URL
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*

data class PleietrengendeMedSak(
    val pleietrengende: PleietrengendeDTO,
    val sak: SakDTO,
)

data class PleietrengendeDTO(
    val identitetsnummer: String,
    val fødselsdato: LocalDate,
    val aktørId: String,

    // Navn vil kunne være null hvis søker ikke har omsorgen for pleietrengende.
    val fornavn: String? = null,
    val mellomnavn: String? = null,
    val etternavn: String? = null,
)

data class SakDTO(
    val saksnummer: Saksnummer,
    val utledetStatus: UtledetStatus,
    val saksbehandlingsFrist: LocalDate? = null,
    @Deprecated("bruk ytelseType")
    val fagsakYtelseType: no.nav.k9.kodeverk.behandling.FagsakYtelseType,
    val ytelseType: FagsakYtelseType,
    val behandlinger: List<BehandlingDTO>,
)

data class UtledetStatus(
    val status: BehandlingStatus,
    val aksjonspunkter: List<AksjonspunktDTO>,
    val saksbehandlingsFrist: LocalDate? = null,
)

data class BehandlingDTO(
    val status: BehandlingStatus,
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DtoKonstanter.DATO_TID_FORMAT,
        timezone = DtoKonstanter.TIDSSONE
    ) val opprettetTidspunkt: ZonedDateTime,
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DtoKonstanter.DATO_TID_FORMAT,
        timezone = DtoKonstanter.TIDSSONE
    ) val avsluttetTidspunkt: ZonedDateTime? = null,
    val innsendelser: List<InnsendelserISakDTO>,
    val aksjonspunkter: List<AksjonspunktDTO>,
    val utgåendeDokumenter: List<DokumentDTO>,
)

data class InnsendelserISakDTO(
    val søknadId: UUID,
    val mottattTidspunkt: ZonedDateTime,
    val innsendelsestype: Innsendelsestype,
    val k9FormatInnsendelse: Innsending? = null,
    val dokumenter: List<DokumentDTO>,
    val arbeidsgivere: List<Organisasjon>? = null,
)

enum class Innsendelsestype {
    SØKNAD, ETTERSENDELSE, ENDRINGSMELDING, UKJENT,
}

data class DokumentDTO(
    val journalpostId: String,
    val dokumentInfoId: String,
    val saksnummer: Saksnummer?,
    val tittel: String,
    val dokumentType: DokumentBrevkode?,
    val filtype: String,
    val harTilgang: Boolean,
    val url: URL,
    @JsonIgnore val journalposttype: Journalposttype,
    val relevanteDatoer: List<RelevantDatoDTO>,
)

enum class DokumentBrevkode {
    PLEIEPENGER_SYKT_BARN_SOKNAD,
    PLEIEPENGER_SYKT_BARN_ETTERSENDELSE,
    ETTERLYST_INNTEKTSMELDING,
    ETTERLYST_INNTEKTSMELDING_PURRING,
    VEDTAK_INNVILGELSE,
    VEDTAK_AVSLAG,
    VEDTAK_FRITEKST,
    VEDTAK_ENDRING,
    VEDTAK_MANUELT,
    VEDTAK_UENDRETUTFALL,
    UKJENT,
}

enum class Journalposttype {
    INNGÅENDE,
    UTGÅENDE,
    NOTAT
}

data class RelevantDatoDTO(
    val dato: String,
    val datotype: Datotype = Datotype.UKJENT,
)

enum class Datotype {
    DATO_OPPRETTET,
    DATO_SENDT_PRINT,
    DATO_EKSPEDERT,
    DATO_JOURNALFOERT,
    DATO_REGISTRERT,
    DATO_AVS_RETUR,
    DATO_DOKUMENT,

    @JsonEnumDefaultValue
    UKJENT,
}

data class AksjonspunktDTO(
    val venteårsak: Aksjonspunkt.Venteårsak,
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DtoKonstanter.DATO_TID_FORMAT,
        timezone = DtoKonstanter.TIDSSONE
    )
    val tidsfrist: ZonedDateTime,
)


