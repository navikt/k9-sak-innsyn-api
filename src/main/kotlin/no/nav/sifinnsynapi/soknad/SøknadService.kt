package no.nav.sifinnsynapi.soknad

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.security.token.support.spring.SpringTokenValidationContextHolder
import no.nav.sifinnsynapi.common.PersonIdentifikator
import no.nav.sifinnsynapi.http.SøknadNotFoundException
import no.nav.sifinnsynapi.util.TokenClaims.CLAIM_PID
import no.nav.sifinnsynapi.util.TokenClaims.CLAIM_SUB
import no.nav.sifinnsynapi.util.personIdent
import org.springframework.stereotype.Service
import java.util.*


@Service
class SøknadService(
    private val repo: SøknadRepository,
    private val tokenValidationContextHolder: SpringTokenValidationContextHolder
) {

    companion object {
        private val mapper = ObjectMapper()
    }

    fun hentSøknader(): List<SøknadDTO> {

        return repo.findAllByPersonIdent(personIdent = tokenValidationContextHolder.personIdent())
            .map { it.tilSøknadDTO() }
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
}

