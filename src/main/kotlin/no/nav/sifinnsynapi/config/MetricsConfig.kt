package no.nav.sifinnsynapi.config

import io.micrometer.common.KeyValue
import io.micrometer.common.KeyValues
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention
import org.springframework.http.server.observation.ServerRequestObservationContext

@Configuration
class MetricsConfig() : DefaultServerRequestObservationConvention() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MetricsConfig::class.java)
    }

    override fun getLowCardinalityKeyValues(context: ServerRequestObservationContext): KeyValues {
        return super.getLowCardinalityKeyValues(context).and(problemDetailsKeyValue(context))
    }

    override fun getHighCardinalityKeyValues(context: ServerRequestObservationContext): KeyValues {
        return super.getLowCardinalityKeyValues(context).and(problemDetailsKeyValue(context))
    }

    private fun problemDetailsKeyValue(context: ServerRequestObservationContext): KeyValue {
        val httpServletResponse = context.response
        return when (val problemDetailsHeader = httpServletResponse?.getHeader("problem-details")) {
            null -> KeyValue.of("problem-details", "n/a")
            else -> KeyValue.of("problem-details", problemDetailsHeader)
        }
    }
}
