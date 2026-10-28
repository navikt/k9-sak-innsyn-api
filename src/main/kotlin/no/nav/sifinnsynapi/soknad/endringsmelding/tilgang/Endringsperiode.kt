package no.nav.sifinnsynapi.soknad.endringsmelding.tilgang

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Perioden bruker kan endre i.
 *
 * - Fra: den 1. i måneden, tre måneder før inneværende måned. Følger søknadsfristvilkåret
 *   (krav kan settes fram for inntil tre måneder før måneden kravet settes fram).
 * - Til: siste dag i inneværende måned, pluss 12 måneder.
 *
 * Eksempel: i dag = 2026-10-26 gir 2026-07-01 – 2027-10-31.
 *
 * Rekkefølgen (månedsslutt før +12 måneder) følger getTillattEndringsperiode i frontend, slik at
 * begge gir samme svar. Februar i et år før skuddår gir derfor 28. februar, ikke 29. februar.
 *
 * [iDag] må regnes ut i Europe/Oslo (se ClockConfiguration), ellers kan perioden bli feil rundt midnatt
 * ved månedsskifte.
 */
fun tillattEndringsperiode(iDag: LocalDate): Datoperiode = Datoperiode(
    fom = iDag.withDayOfMonth(1).minusMonths(3),
    tom = iDag.with(TemporalAdjusters.lastDayOfMonth()).plusMonths(12),
)
