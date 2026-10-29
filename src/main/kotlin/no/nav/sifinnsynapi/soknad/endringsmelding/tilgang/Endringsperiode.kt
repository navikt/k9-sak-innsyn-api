package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Perioden bruker kan endre i: fra den 1. i måneden tre måneder tilbake fra dagens dato, til siste dag
 * i måneden 12 måneder frem i tid.
 */
fun tillattEndringsperiode(iDag: LocalDate): Datoperiode = Datoperiode(
    fom = iDag.withDayOfMonth(1).minusMonths(3),
    tom = iDag.plusMonths(12).with(TemporalAdjusters.lastDayOfMonth()),
)
