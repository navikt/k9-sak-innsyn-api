package no.nav.sifinnsynapi.sak.inntektsmelding

import no.nav.sifinnsynapi.config.TxConfiguration.Companion.TRANSACTION_MANAGER
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Stream

@Transactional(TRANSACTION_MANAGER)
interface InntektsmeldingRepository : JpaRepository<InntektsmeldingDAO, String> {
    fun findAllBySøkerAktørIdAndSaksnummerOrderByOppdatertDatoAsc(
        søkerAktørId: String,
        saksnummer: String,
    ): Stream<InntektsmeldingDAO>
}
