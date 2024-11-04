package no.nav.sifinnsynapi.k9sak.opplaeringsinstitusjon

import no.nav.k9.sak.typer.Periode
import java.util.*

data class Opplæringsinstitusjon(
    val uuid: UUID,
    val navn: String,
    val perioder: List<Periode>
)
