package no.nav.sifinnsynapi.sak

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import no.nav.k9.innsyn.sak.Aksjonspunkt
import no.nav.k9.innsyn.sak.BehandlingStatus
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.sak.typer.Saksnummer
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.Kildesystem
import java.net.URL
import java.time.LocalDate

data class PleietrengendeMedSak(
    val pleietrengende: PleietrengendeDTO,
    val sak: SakDTO,
)

data class PleietrengendeDTO(
    val identitetsnummer: String,
    val fødselsdato: LocalDate,
    val fornavn: String,
    val mellomnavn: String? = null,
    val etternavn: String,
    val aktørId: String,
)

data class SakDTO(
    val saksnummer: Saksnummer,
    val saksbehandlingsFrist: LocalDate? = null,
    val fagsakYtelseType: FagsakYtelseType,
    val behandlinger: List<BehandlingDTO>,
)

data class BehandlingDTO(
    val status: BehandlingStatus,
    val søknader: List<SøknaderISakDTO>,
    val aksjonspunkter: List<AksjonspunktDTO>,
)

data class SøknaderISakDTO(
    // Kan være null for eldre søknader
    val kildesystem: Kildesystem?,
    val k9FormatSøknad: Søknad,
    val dokumenter: List<DokumentDTO>,
)

data class DokumentDTO(
    val journalpostId: String,
    val dokumentInfoId: String,
    val saksnummer: Saksnummer?,
    val tittel: String,
    val filtype: String,
    val harTilgang: Boolean,
    val url: URL,
    val relevanteDatoer: List<RelevantDatoDTO>,
)

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
    val venteårsak: Aksjonspunkt.Venteårsak
)


