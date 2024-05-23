package no.nav.sifinnsynapi.common

import org.springframework.context.annotation.Profile

/**
 * Markerer at en komponent ikke er aktivert i produksjon.
 * Dersom en klasse eller en metode er annotert med denne, vil det ikke bli opprettet en bean av denne i produksjonsprofilen.
 */
@Profile("!prod")
annotation class IkkeAktivertIProduksjon
