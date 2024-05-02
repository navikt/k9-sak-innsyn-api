package no.nav.sifinnsynapi.soknad

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface EttersendelseRepository : JpaRepository<EttersendelseDAO, String> {
    @Query("""
        select e from ettersendelse e 
            where e.journalpostId = :journalpostId  
            order by e.oppdatertDato asc 
    """)
    fun finnForJournalpost(journalpostId: String): List<EttersendelseDAO>
}