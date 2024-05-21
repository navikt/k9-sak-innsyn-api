package no.nav.sifinnsynapi.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponents
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono

@Component
class CustomHealthIndicator(@Qualifier("k9OppslagsKlient") private val oppslagsKlient: RestTemplate,
                            @Qualifier("k9SakKlient") private val k9SakKlient: RestTemplate) : ReactiveHealthIndicator {
    companion object{

        val healthUrl = UriComponentsBuilder
                .fromUriString("/isalive")
                .build()
    }

    private fun oppslagHelsesjekk(){
        val healthUrl = UriComponentsBuilder
            .fromUriString("/isalive")
            .build()
        oppslagsKlient.exchange(healthUrl.toUriString(), HttpMethod.GET, null, String::class.java)
    }

    private fun k9SakHelsesjekk(){
        val healthUrl = UriComponentsBuilder
            .fromUriString("/sak/internal/health/isAlive")
            .build()
        k9SakKlient.exchange(healthUrl.toUriString(), HttpMethod.GET, null, String::class.java)
    }

    override fun health(): Mono<Health> {
        return try {
            oppslagHelsesjekk()
            k9SakHelsesjekk()
            Mono.just(Health.Builder().up().withDetail("k9-selvbetjening-oppslag", "HEALTHY AND ALIVE").build())
        } catch (exception: HttpServerErrorException){
            Mono.just(Health.Builder().down(exception).withDetail("k9-selvbetjening-oppslag", "UNHEALTHY AND DEAD").build())
        }
    }


}