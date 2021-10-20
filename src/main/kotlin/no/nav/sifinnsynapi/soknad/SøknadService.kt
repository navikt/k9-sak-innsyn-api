package no.nav.sifinnsynapi.soknad

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.Versjon
import no.nav.k9.søknad.felles.personopplysninger.Barn
import no.nav.k9.søknad.felles.personopplysninger.Søker
import no.nav.k9.søknad.felles.type.Journalpost
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.type.Periode
import no.nav.k9.søknad.felles.type.Språk
import no.nav.k9.søknad.ytelse.psb.v1.*
import no.nav.k9.søknad.ytelse.psb.v1.Beredskap.BeredskapPeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.LovbestemtFerie.LovbestemtFeriePeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.Nattevåk.NattevåkPeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstaker
import no.nav.k9.søknad.ytelse.psb.v1.arbeidstid.Arbeidstid
import no.nav.k9.søknad.ytelse.psb.v1.tilsyn.TilsynPeriodeInfo
import no.nav.k9.søknad.ytelse.psb.v1.tilsyn.Tilsynsordning
import no.nav.security.token.support.spring.SpringTokenValidationContextHolder
import no.nav.sifinnsynapi.http.SøknadNotFoundException
import no.nav.sifinnsynapi.util.komplettYtelse
import no.nav.sifinnsynapi.util.personIdent
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*
import java.util.Map
import kotlin.collections.List
import kotlin.collections.listOf
import kotlin.collections.map


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
            .medVersjon(Versjon.of("1.0"))
            .medYtelse(
                komplettYtelse(Periode(LocalDate.parse("2021-08-12"), LocalDate.parse("2022-01-12")))
            )
        check(PleiepengerSyktBarnSøknadValidator().valider(søknad).isNotEmpty()) { "Mocking av søknadsopplysninger feilet validering" }
        return SøknadDTO(
            søknadId = søknadId,
            søknad = søknad,
            opprettet = null,
            endret = null,
            behandlingsdato = null
        )
        /*return repo.findAllByPersonIdent(personIdent = tokenValidationContextHolder.personIdent())
            .map { it.tilSøknadDTO() }*/
    }

    fun hentSøknad(søknadId: UUID): SøknadDTO {
        val søknadDAO = repo.findBySøknadId(søknadId) ?: throw SøknadNotFoundException(søknadId.toString())
        return søknadDAO.tilSøknadDTO()
    }

    fun SøknadDAO.tilSøknadDTO() = SøknadDTO(
        søknadId = søknadId,
        opprettet = opprettet,
        endret = endret,
        behandlingsdato = behandlingsdato,
        søknad = søknad
    )

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
                    .medVersjon(Versjon.of("1.0"))
                    .medYtelse(
                        komplettYtelse(Periode(LocalDate.now().minusWeeks(1), LocalDate.now().plusWeeks(1)))
                    ),
                opprettet = null,
                endret = null,
                behandlingsdato = null
            )
        )
    }
}

