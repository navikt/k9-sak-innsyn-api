package no.nav.sifinnsynapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class ClockConfiguration {

    /** Norsk tid, slik at «i dag» blir riktig rundt midnatt. Kan erstattes med en fast klokke i tester. */
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Europe/Oslo"))
}
