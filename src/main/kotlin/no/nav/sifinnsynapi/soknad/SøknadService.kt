package no.nav.sifinnsynapi.soknad

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.sifinnsynapi.common.AktørId
import no.nav.sifinnsynapi.http.SøknadNotFoundException
import no.nav.sifinnsynapi.oppslag.OppslagsService
import org.springframework.stereotype.Service
import java.util.*

@Service
class SøknadService(
    private val repo: SøknadRepository,
    private val oppslagsService: OppslagsService
) {

    companion object {
        private val mapper = ObjectMapper()
    }

    fun hentSøknader(): List<SøknadDTO> {

        val aktørId = AktørId.valueOf(oppslagsService.hentAktørId()!!.aktør_id)

        return repo.findAllByAktørId(aktørId).map {
            it.tilSøknadDTO()
        }
    }

    fun hentSøknad(søknadId: UUID): SøknadDTO {
        return repo.findById(søknadId).orElseThrow {
            SøknadNotFoundException(søknadId.toString())
        }.tilSøknadDTO()
    }

    fun SøknadDAO.tilSøknadDTO() = SøknadDTO(
        søknadId = id,
        opprettet = opprettet,
        endret = endret,
        behandlingsdato = behandlingsdato,
        søknad = søknad
    )
}

