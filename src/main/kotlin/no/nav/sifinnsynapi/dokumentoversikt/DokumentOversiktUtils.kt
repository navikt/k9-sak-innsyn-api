package no.nav.sifinnsynapi.dokumentoversikt

import no.nav.k9.kodeverk.dokument.Brevkode
import no.nav.sifinnsynapi.safselvbetjening.generated.hentdokumentoversikt.Dokumentoversikt

object DokumentOversiktUtils {
    fun Dokumentoversikt.medRelevanteBrevkoder(relevanteBrevkoder: List<String>): Dokumentoversikt {
        val journalposterMedRelevanteDokumenter = journalposter
            .filterNot { it.dokumenter.isNullOrEmpty() }
            .map { journalpost ->
                // Filtrer hvert dokument innenfor en journalpost basert på relevante brevkoder
                val relevanteDokumenter = journalpost.dokumenter!!
                    .filterNotNull()
                    .filter { dokumentInfo ->
                        dokumentInfo.brevkode?.lowercase()
                            ?.trim() in relevanteBrevkoder.map { it.lowercase().trim() }
                    }

                // Returner en ny journalpost med kun de relevante dokumentene
                journalpost.copy(dokumenter = relevanteDokumenter)
            }.filter {
                // Fjern journalposter som ikke har noen relevante dokumenter etter filtrering
                it.dokumenter!!.isNotEmpty()
            }

        return Dokumentoversikt(journalposter = journalposterMedRelevanteDokumenter)
    }
}
