package no.nav.sifinnsynapi.dokumentoversikt

import no.nav.k9.kodeverk.dokument.Brevkode
import no.nav.sifinnsynapi.dokumentoversikt.DokumentOversiktUtils.medRelevanteBrevkoder
import no.nav.sifinnsynapi.safselvbetjening.generated.enums.Journalstatus
import no.nav.sifinnsynapi.safselvbetjening.generated.hentdokumentoversikt.DokumentInfo
import no.nav.sifinnsynapi.safselvbetjening.generated.hentdokumentoversikt.Dokumentoversikt
import no.nav.sifinnsynapi.safselvbetjening.generated.hentdokumentoversikt.Journalpost

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DokumentoversiktTest {

    private companion object {
        val relevanteBrevkoder = listOf(
            Brevkode.PLEIEPENGER_BARN_SOKNAD,
            Brevkode.ETTERSENDELSE_PLEIEPENGER_SYKT_BARN
        )
    }

    @Test
    fun `Gitt dokumentoversikt med journalpost uten dokumenter, forvent ingen journalposter`() {
        val journalpostUtenDokumenter = Journalpost(
            journalpostId = "123",
            tittel = "tittel",
            journalstatus = Journalstatus.JOURNALFOERT,
            relevanteDatoer = listOf(),
            sak = null,
            dokumenter = listOf()
        )
        val dokumentoversikt = Dokumentoversikt(journalposter = listOf(journalpostUtenDokumenter))

        val resultat = dokumentoversikt.medRelevanteBrevkoder(relevanteBrevkoder)
        assertEquals(0, resultat.journalposter.size)
    }

    @Test
    fun `Gitt dokumentoversikt med journalpost med dokument som har irrelevant brevkode, forvent ingen journalposter`() {
        val journalpostMedIrrelevantDokument = Journalpost(
            journalpostId = "123",
            tittel = "tittel",
            journalstatus = Journalstatus.JOURNALFOERT,
            relevanteDatoer = listOf(),
            sak = null,
            dokumenter = listOf(
                DokumentInfo(
                    dokumentInfoId = "123",
                    tittel = "tittel",
                    brevkode = "IKKE_RELEVANT",
                    dokumentvarianter = listOf()
                )
            )
        )
        val dokumentoversikt = Dokumentoversikt(journalposter = listOf(journalpostMedIrrelevantDokument))
        val resultat = dokumentoversikt.medRelevanteBrevkoder(relevanteBrevkoder)
        assertEquals(0, resultat.journalposter.size)
    }

    @Test
    fun `Gitt dokumentoversikt med journalposter relevant dokument og irrelevant dokument, forvent kun relevant journalpost`() {
        val journalpostMedRelevantDokumenter = Journalpost(
            journalpostId = "123",
            tittel = "tittel",
            journalstatus = Journalstatus.JOURNALFOERT,
            relevanteDatoer = listOf(),
            sak = null,
            dokumenter = listOf(
                DokumentInfo(
                    dokumentInfoId = "123",
                    tittel = "tittel",
                    brevkode = Brevkode.PLEIEPENGER_BARN_SOKNAD.offisiellKode,
                    dokumentvarianter = listOf()
                ),
                DokumentInfo(
                    dokumentInfoId = "123",
                    tittel = "tittel",
                    brevkode = Brevkode.ETTERSENDELSE_PLEIEPENGER_SYKT_BARN.offisiellKode,
                    dokumentvarianter = listOf()
                )
            )
        )

        val journalpostMedIrrelevantDokument = Journalpost(
            journalpostId = "123",
            tittel = "tittel",
            journalstatus = Journalstatus.JOURNALFOERT,
            relevanteDatoer = listOf(),
            sak = null,
            dokumenter = listOf(
                DokumentInfo(
                    dokumentInfoId = "123",
                    tittel = "tittel",
                    brevkode = "IKKE_RELEVANT",
                    dokumentvarianter = listOf()
                )
            )
        )

        val dokumentoversikt = Dokumentoversikt(
            journalposter = listOf(
                journalpostMedRelevantDokumenter,
                journalpostMedIrrelevantDokument
            )
        )
        val resultat = dokumentoversikt.medRelevanteBrevkoder(relevanteBrevkoder)
        assertEquals(1, resultat.journalposter.size)
        assertEquals(2, resultat.journalposter.first().dokumenter!!.size)
    }

    @Test
    fun `Gitt dokumentoversikt med journalpost som har relevant og irrelevant dokument, forvent kun relevante dokumenter`() {
        val journalpostMedRelevanteOgIrrelevanteDokumenter = Journalpost(
            journalpostId = "123",
            tittel = "tittel",
            journalstatus = Journalstatus.JOURNALFOERT,
            relevanteDatoer = listOf(),
            sak = null,
            dokumenter = listOf(
                DokumentInfo(
                    dokumentInfoId = "123",
                    tittel = "tittel",
                    brevkode = Brevkode.PLEIEPENGER_BARN_SOKNAD.offisiellKode,
                    dokumentvarianter = listOf()
                ),
                DokumentInfo(
                    dokumentInfoId = "123",
                    tittel = "tittel",
                    brevkode = Brevkode.ETTERSENDELSE_PLEIEPENGER_SYKT_BARN.offisiellKode,
                    dokumentvarianter = listOf()
                ),
                DokumentInfo(
                    dokumentInfoId = "123",
                    tittel = "tittel",
                    brevkode = "IKKE_RELEVANT",
                    dokumentvarianter = listOf()
                )
            )
        )

        val dokumentoversikt = Dokumentoversikt(journalposter = listOf(journalpostMedRelevanteOgIrrelevanteDokumenter))
        val resultat = dokumentoversikt.medRelevanteBrevkoder(relevanteBrevkoder)
        assertEquals(1, resultat.journalposter.size)
        assertEquals(2, resultat.journalposter.first().dokumenter!!.size)
    }
}
