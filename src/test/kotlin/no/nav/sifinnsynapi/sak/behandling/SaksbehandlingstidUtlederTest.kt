package no.nav.sifinnsynapi.sak.behandling

import no.nav.k9.innsyn.sak.*
import no.nav.k9.søknad.felles.Kildesystem
import org.junit.jupiter.api.Test
import org.springframework.cloud.contract.verifier.assertion.SpringCloudContractAssertions.assertThat
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.stream.Collectors

class SaksbehandlingstidUtlederTest {


    @Test
    fun `skal regne ut saksbehandlingsfrist`() {
        val tidligsteMottattTidspunkt = LocalDate.of(2024, 1, 5).atStartOfDay(ZoneId.systemDefault())
        val behandling = lagBehandling(
            false,
            tidligsteMottattTidspunkt.plusDays(10),
            tidligsteMottattTidspunkt,
            tidligsteMottattTidspunkt.plusMonths(20)
        )
        val saksbehandlingsfrist = SaksbehandlingstidUtleder.utled(behandling,(null))

        assertThat(saksbehandlingsfrist).isEqualTo(tidligsteMottattTidspunkt.plusWeeks(6))
    }

    @Test
    fun `skal overstyre og regne ut saksbehandlingsfrist`() {
        val tidligsteMottattTidspunkt = LocalDate.of(2024, 1, 5).atStartOfDay(ZoneId.systemDefault())
        val behandling = lagBehandling(false, tidligsteMottattTidspunkt.plusDays(10), tidligsteMottattTidspunkt, tidligsteMottattTidspunkt.plusMonths(20))
        val saksbehandlingsfrist = SaksbehandlingstidUtleder.utled(behandling, Period.ofDays(5))
        assertThat(saksbehandlingsfrist).isEqualTo(tidligsteMottattTidspunkt.plusDays(5))
    }

    @Test
    fun `skal regne ut saksbehandlingsfrist utland`() {
        val tidligsteMottattTidspunkt = LocalDate.of(2024, 1, 5).atStartOfDay(ZoneId.systemDefault())
        val behandling = lagBehandling(true, tidligsteMottattTidspunkt.plusDays(10), tidligsteMottattTidspunkt, tidligsteMottattTidspunkt.plusMonths(20))
        val saksbehandlingsfrist = SaksbehandlingstidUtleder.utled(behandling)
        assertThat(saksbehandlingsfrist).isEqualTo(tidligsteMottattTidspunkt.plusMonths(6))
    }


    @Test
    fun `skal regne ut saksbehandlingsfrist men ignorere ettersendelse`() {
        val tidligsteMottattTidspunkt = LocalDate.of(2024, 1, 5).atStartOfDay(ZoneId.systemDefault())
        val tidligsteSøknadsTidspunkt = tidligsteMottattTidspunkt.plusDays(10)
        val behandling = lagBehandling(false, setOf(
            lagSøknad(tidligsteSøknadsTidspunkt, Kildesystem.SØKNADSDIALOG),
            lagEttersendelse(tidligsteMottattTidspunkt),
            lagSøknad(tidligsteMottattTidspunkt.plusMonths(20), Kildesystem.SØKNADSDIALOG)
        ))
        val saksbehandlingsfrist = SaksbehandlingstidUtleder.utled(behandling)
        assertThat(saksbehandlingsfrist).isEqualTo(tidligsteSøknadsTidspunkt.plusWeeks(6))

    }

    @Test
    fun `skal ikke regne ut saksbehandlingsfrist hvis inneholder punsj`() {
        val tidligsteMottattTidspunkt = LocalDate.of(2024, 1, 5).atStartOfDay(ZoneId.systemDefault())
        val behandling = lagBehandling(false, setOf(
            lagSøknad(tidligsteMottattTidspunkt.plusDays(10), Kildesystem.SØKNADSDIALOG),
            lagSøknad(tidligsteMottattTidspunkt, Kildesystem.SØKNADSDIALOG),
            lagSøknad(tidligsteMottattTidspunkt.plusMonths(20), Kildesystem.PUNSJ)
        ))
        val saksbehandlingsfrist = SaksbehandlingstidUtleder.utled(behandling)
        assertThat(saksbehandlingsfrist).isNull()
    }

    @Test
    fun `skal ikke regne ut saksbehandlingsfrist hvis mangler kildesystem`() {
        val tidligsteMottattTidspunkt = LocalDate.of(2024, 1, 5).atStartOfDay(ZoneId.systemDefault())
        val behandling = lagBehandling(false, setOf(
            lagSøknad(tidligsteMottattTidspunkt.plusDays(10), Kildesystem.SØKNADSDIALOG),
            lagSøknad(tidligsteMottattTidspunkt, Kildesystem.SØKNADSDIALOG),
            lagSøknad(tidligsteMottattTidspunkt.plusMonths(20))
        ))
        val saksbehandlingsfrist = SaksbehandlingstidUtleder.utled(behandling)
        assertThat(saksbehandlingsfrist).isNull()
    }

    @Test
    fun `skal ikke regne ut saksbehandlingsfrist hvis ingen søknad`() {
        val behandling = lagBehandling(false, emptySet())
        val saksbehandlingsfrist = SaksbehandlingstidUtleder.utled(behandling)
        assertThat(saksbehandlingsfrist).isNull()
    }

    private fun lagBehandling(erUtenlands: Boolean, vararg søknadtidspunkter: ZonedDateTime): Behandling {
        val collect = Arrays.stream(søknadtidspunkter)
            .map { it: ZonedDateTime ->
                lagSøknad(
                    it,
                    Kildesystem.SØKNADSDIALOG
                )
            }
            .collect(Collectors.toSet())
        return lagBehandling(erUtenlands, collect)
    }

    private fun lagSøknad(it: ZonedDateTime, kilde: Kildesystem?  = null): InnsendingInfo {
        return InnsendingInfo(InnsendingStatus.MOTTATT, UUID.randomUUID().toString(), it, kilde, InnsendingType.SØKNAD)
    }

    private fun lagEttersendelse(mottatt: ZonedDateTime): InnsendingInfo {
        return InnsendingInfo(InnsendingStatus.MOTTATT, UUID.randomUUID().toString(), mottatt, null, InnsendingType.ETTERSENDELSE)
    }

    private fun lagBehandling(erUtenlands: Boolean, søknader: Set<InnsendingInfo>): Behandling {
        val aksjonspunkter = java.util.Set.of(
            Aksjonspunkt(Aksjonspunkt.Venteårsak.MEDISINSK_DOKUMENTASJON, ZonedDateTime.now())
        )

        val saksnummer = "ABC123"
        val søkerAktørId = "11111111111"
        val pleietrengendeAktørId = "22222222222"

        val saksinnhold = Fagsak(
            Saksnummer(saksnummer),
            AktørId(søkerAktørId),
            AktørId(pleietrengendeAktørId),
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN
        )

        val behandling = Behandling(
            UUID.randomUUID(),
            ZonedDateTime.now(),
            null,
            BehandlingResultat.INNVILGET,
            BehandlingStatus.OPPRETTET,
            søknader,
            aksjonspunkter,
            erUtenlands,
            saksinnhold

        )

        return behandling
    }

}
