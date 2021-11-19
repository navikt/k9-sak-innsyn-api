package no.nav.sifinnsynapi.soknad

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.Versjon
import no.nav.k9.søknad.felles.personopplysninger.Søker
import no.nav.k9.søknad.felles.type.Journalpost
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.type.Periode
import no.nav.k9.søknad.felles.type.Språk
import no.nav.k9.søknad.ytelse.psb.v1.*
import no.nav.security.token.support.spring.SpringTokenValidationContextHolder
import no.nav.sifinnsynapi.util.komplettYtelse
import no.nav.sifinnsynapi.util.personIdent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*
import java.util.stream.Stream


@Service
class SøknadService(
    private val repo: SøknadRepository,
    private val tokenValidationContextHolder: SpringTokenValidationContextHolder
) {

    companion object {
        private val mapper = ObjectMapper()
    }

    fun hentSøknadsopplysninger(): SøknadDTO {

        val søknadId = UUID.randomUUID()
        val søknad = Søknad()
            .medSøknadId(søknadId.toString())
            .medSøker(Søker(NorskIdentitetsnummer.of(tokenValidationContextHolder.personIdent().personIdent)))
            .medJournalpost(Journalpost().medJournalpostId("123456789"))
            .medSpråk(Språk.NORSK_BOKMÅL)
            .medMottattDato(ZonedDateTime.now())
            .medVersjon(Versjon.of("1.0.0"))
            .medYtelse(
                komplettYtelse(Periode(LocalDate.parse("2021-08-12"), LocalDate.parse("2022-01-12")))
            )

        PleiepengerSyktBarnSøknadValidator().forsikreValidert(søknad)
        return SøknadDTO(
            søknadId = søknadId,
            søknad = søknad,
            opprettetDato = null,
            oppdatertDato = null,
            behandlingsdato = null
        )
        /*return repo.findAllByPersonIdent(personIdent = tokenValidationContextHolder.personIdent())
            .map { it.tilSøknadDTO() }*/
    }

    fun PsbSøknadDAO.tilSøknadDTO(): SøknadDTO {
        val søknad = JsonUtils.fromString(søknad, Søknad::class.java)
        return SøknadDTO(
            søknadId = UUID.fromString(søknad.søknadId.id),
            opprettetDato = opprettetDato,
            oppdatertDato = oppdatertDato,
            søknad = søknad
        )
    }

    fun hentTestData(): List<SøknadDTO> {
        val søknadId = UUID.randomUUID()

        return listOf(
            SøknadDTO(
                søknadId = søknadId,
                søknad = Søknad()
                    .medSøknadId(søknadId.toString())
                    .medSøker(Søker(NorskIdentitetsnummer.of(tokenValidationContextHolder.personIdent().personIdent)))
                    .medJournalpost(Journalpost().medJournalpostId("123456789"))
                    .medSpråk(Språk.NORSK_BOKMÅL)
                    .medMottattDato(ZonedDateTime.now())
                    .medVersjon(Versjon.of("1.0.0"))
                    .medYtelse(
                        komplettYtelse(Periode(LocalDate.now().minusWeeks(1), LocalDate.now().plusWeeks(1)))
                    ),
                opprettetDato = null,
                oppdatertDato = null,
                behandlingsdato = null
            )
        )
    }
}

