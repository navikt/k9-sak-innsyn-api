package no.nav.sifinnsynapi.endringsmelding.psb

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Perioden bruker kan endre i.
 *
 * - Fra: den 1. i måneden, tre måneder før inneværende måned. Følger søknadsfristvilkåret
 *   (krav kan settes fram for inntil tre måneder før måneden kravet settes fram).
 * - Til: siste dag i inneværende måned, pluss 12 måneder. Rekkefølgen følger frontend: februar 2027 gir
 *   2028-02-28, ikke 2028-02-29.
 *
 * Eksempel: i dag = 2026-10-26 gir 2026-07-01 – 2027-10-31.
 *
 * [iDag] må regnes ut i Europe/Oslo (se ClockConfiguration), ellers kan perioden bli feil rundt midnatt
 * ved månedsskifte.
 */
fun tillattEndringsperiode(iDag: LocalDate): Datoperiode = Datoperiode(
    fom = iDag.withDayOfMonth(1).minusMonths(3),
    tom = iDag.with(TemporalAdjusters.lastDayOfMonth()).plusMonths(12),
)
