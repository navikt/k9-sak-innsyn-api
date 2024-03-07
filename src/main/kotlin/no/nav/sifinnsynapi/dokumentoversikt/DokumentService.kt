package no.nav.sifinnsynapi.dokumentoversikt

import kotlinx.coroutines.runBlocking
import no.nav.k9.formidling.kontrakt.kodeverk.DokumentMalType
import no.nav.k9.kodeverk.dokument.Brevkode
import no.nav.k9.sak.typer.Saksnummer
import no.nav.sifinnsynapi.Routes
import no.nav.sifinnsynapi.dokumentoversikt.DokumentOversiktUtils.medRelevanteBrevkoder
import no.nav.sifinnsynapi.safselvbetjening.generated.enums.Datotype
import no.nav.sifinnsynapi.safselvbetjening.generated.enums.Variantformat
import no.nav.sifinnsynapi.safselvbetjening.generated.hentdokumentoversikt.DokumentInfo
import no.nav.sifinnsynapi.safselvbetjening.generated.hentdokumentoversikt.Dokumentoversikt
import no.nav.sifinnsynapi.safselvbetjening.generated.hentdokumentoversikt.RelevantDato
import no.nav.sifinnsynapi.sak.DokumentDTO
import no.nav.sifinnsynapi.sak.Journalposttype
import no.nav.sifinnsynapi.sak.RelevantDatoDTO
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URL

@Service
class DokumentService(
    private val safSelvbetjeningService: SafSelvbetjeningService,
    @Value("\${application-ingress}") val applicationIngress: String
) {

    companion object {
        private val logger = LoggerFactory.getLogger(DokumentService::class.java)

        val RELEVANTE_BREVKODER: List<String> = listOf(
            // Søknader
            Brevkode.PLEIEPENGER_BARN_SOKNAD.offisiellKode,
            Brevkode.ETTERSENDELSE_PLEIEPENGER_SYKT_BARN.offisiellKode,

            // Inntektsmelding
            DokumentMalType.ETTERLYS_INNTEKTSMELDING_DOK.kode,
            DokumentMalType.ETTERLYS_INNTEKTSMELDING_PURRING.kode,

            // Vedtak
            DokumentMalType.INNVILGELSE_DOK.kode,
            DokumentMalType.AVSLAG__DOK.kode,
            DokumentMalType.FRITEKST_DOK.kode,
            DokumentMalType.ENDRING_DOK.kode,
            DokumentMalType.MANUELT_VEDTAK_DOK.kode,
            DokumentMalType.UENDRETUTFALL_DOK.kode,
        )
    }

    fun hentDokumentOversikt(): List<DokumentDTO> = runBlocking {
        safSelvbetjeningService.hentDokumentoversikt()
            .medRelevanteBrevkoder(RELEVANTE_BREVKODER)
            .somDokumentDTO(applicationIngress)
    }

    fun hentDokument(journalpostId: String, dokumentInfoId: String, varianFormat: String): ArkivertDokument {
        logger.info("Henter dokument for journalpostId: {}", journalpostId)
        return safSelvbetjeningService.hentDokument(journalpostId, dokumentInfoId, varianFormat)
    }

    private fun Dokumentoversikt.somDokumentDTO(applicationIngress: String): List<DokumentDTO> =
        journalposter.flatMap { journalpost ->
            journalpost.dokumenter!!.map { dokumentInfo: DokumentInfo? ->
                val journalpostId = journalpost.journalpostId
                val sakId = journalpost.sak?.fagsakId
                val relevanteDatoer = journalpost.relevanteDatoer.map { it!! }

                val dokumentInfoId = dokumentInfo!!.dokumentInfoId
                val dokumentvariant =
                    dokumentInfo.dokumentvarianter.first { it!!.variantformat == Variantformat.ARKIV }!!
                val brukerHarTilgang = dokumentvariant.brukerHarTilgang
                val tittel = dokumentInfo.tittel!!

                DokumentDTO(
                    journalpostId = journalpostId,
                    saksnummer = sakId?.let { Saksnummer(it) },
                    dokumentInfoId = dokumentInfoId,
                    filtype = dokumentvariant.filtype,
                    tittel = tittel,
                    brevkode = dokumentInfo.brevkode,
                    harTilgang = brukerHarTilgang,
                    journalposttype = when(val jpt = journalpost.journalposttype) {
                        no.nav.sifinnsynapi.safselvbetjening.generated.enums.Journalposttype.I -> Journalposttype.INNGÅENDE
                        no.nav.sifinnsynapi.safselvbetjening.generated.enums.Journalposttype.U -> Journalposttype.UTGÅENDE
                        no.nav.sifinnsynapi.safselvbetjening.generated.enums.Journalposttype.N ->Journalposttype.NOTAT
                        else -> throw IllegalArgumentException("Ukjent journalposttype: $jpt") // Burde ikke kunne inntreffe.
                    },
                    url = URL("$applicationIngress${Routes.DOKUMENT}/$journalpostId/$dokumentInfoId/${dokumentvariant.variantformat.name}"),
                    relevanteDatoer = relevanteDatoer.someRelevanteDatoer()
                )
            }
        }

    private fun List<RelevantDato>.someRelevanteDatoer(): List<RelevantDatoDTO> = map {
        RelevantDatoDTO(
            dato = it.dato,
            datotype = when(it.datotype) {
                Datotype.DATO_OPPRETTET -> no.nav.sifinnsynapi.sak.Datotype.DATO_OPPRETTET
                Datotype.DATO_SENDT_PRINT -> no.nav.sifinnsynapi.sak.Datotype.DATO_SENDT_PRINT
                Datotype.DATO_EKSPEDERT -> no.nav.sifinnsynapi.sak.Datotype.DATO_EKSPEDERT
                Datotype.DATO_JOURNALFOERT -> no.nav.sifinnsynapi.sak.Datotype.DATO_JOURNALFOERT
                Datotype.DATO_REGISTRERT -> no.nav.sifinnsynapi.sak.Datotype.DATO_REGISTRERT
                Datotype.DATO_AVS_RETUR -> no.nav.sifinnsynapi.sak.Datotype.DATO_AVS_RETUR
                Datotype.DATO_DOKUMENT -> no.nav.sifinnsynapi.sak.Datotype.DATO_DOKUMENT
                Datotype.__UNKNOWN_VALUE -> no.nav.sifinnsynapi.sak.Datotype.UKJENT
            }
        )
    }
}
