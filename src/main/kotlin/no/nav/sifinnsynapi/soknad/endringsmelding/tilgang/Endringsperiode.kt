package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Perioden bruker kan endre i: fra den 1. i måneden tre måneder tilbake (søknadsfristen), til siste dag
 * i inneværende måned pluss 12 måneder. Eksempel: 2026-10-26 gir 2026-07-01 – 2027-10-31.
 *
 * Månedsslutt finnes før +12 måneder, så 2023-02-28 gir 2024-02-28, ikke 2024-02-29.
 * [iDag] må være i Europe/Oslo, se ClockConfiguration.
 */
fun tillattEndringsperiode(iDag: LocalDate): Datoperiode = Datoperiode(
    fom = iDag.withDayOfMonth(1).minusMonths(3),
    tom = iDag.with(TemporalAdjusters.lastDayOfMonth()).plusMonths(12),
)
