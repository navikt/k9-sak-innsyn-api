package no.nav.sifinnsynapi.sak.inntektsmelding

import no.nav.k9.innsyn.sak.FagsakYtelseType
import org.springframework.stereotype.Service
import java.util.stream.Stream

@Service
class InntektsmeldingService(private val inntektsmeldingRepository: InntektsmeldingRepository) {
    fun lagreInntektsmelding(inntektsmeldingDAO: InntektsmeldingDAO) {
        inntektsmeldingRepository.save(inntektsmeldingDAO)
    }

    fun hentInntektsmeldinger(søkerAktørId: String, ytelsetype: FagsakYtelseType): Stream<InntektsmeldingDAO> {
        return inntektsmeldingRepository.findAllBySøkerAktørIdAndYtelsetypeOrderByOppdatertDatoAsc(søkerAktørId, ytelsetype)
    }
}
