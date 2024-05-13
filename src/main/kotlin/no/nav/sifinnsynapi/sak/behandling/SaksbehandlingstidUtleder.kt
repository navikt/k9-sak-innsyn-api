package no.nav.sifinnsynapi.sak.behandling

import no.nav.k9.innsyn.sak.Behandling
import no.nav.k9.konstant.Konstant
import no.nav.k9.søknad.felles.Kildesystem
import org.slf4j.LoggerFactory
import java.time.Period
import java.time.ZonedDateTime

object SaksbehandlingstidUtleder {
    private val log = LoggerFactory.getLogger(SaksbehandlingstidUtleder::class.java)

    fun utled(behandling: Behandling, overstyrSaksbehandlingstid: Period? = null): ZonedDateTime? {
        if (behandling.avsluttetTidspunkt != null) {
            log.info("beregner ikke frist for avsluttet behandling")
            return null
        }

        val kildesystemer = behandling.innsendinger.map { it.kildesystem?.kode }
        if (kildesystemer.isEmpty() || !kildesystemer.all { it == Kildesystem.SØKNADSDIALOG.kode || it == Kildesystem.ENDRINGSDIALOG.kode }) {
            log.info("beregner ikke frist for behandlinger som har dokumenter med kildesystemer={}", kildesystemer)
            return null
        }

        val tidligsteMottattDato = behandling.innsendinger.minByOrNull { it.mottattTidspunkt }?.mottattTidspunkt

        val saksbehandlingstid = overstyrSaksbehandlingstid ?:
        if (behandling.erUtenlands) {
            log.info("Beregner frist for utland")
            Konstant.UTLAND_FORVENTET_SAKSBEHANDLINGSTID
        } else {
            log.info("Beregner frist for vanlig sak")
            Konstant.FORVENTET_SAKSBEHANDLINGSTID
        }

        return tidligsteMottattDato?.plus(saksbehandlingstid)
    }
}
