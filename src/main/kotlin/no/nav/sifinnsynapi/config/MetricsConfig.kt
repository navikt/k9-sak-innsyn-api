package no.nav.sifinnsynapi.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.metrics.web.servlet.DefaultWebMvcTagsProvider
import org.springframework.context.annotation.Configuration
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Configuration
class MetricsConfig(val mapper: ObjectMapper) : DefaultWebMvcTagsProvider() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MetricsConfig::class.java)
    }

    override fun getTags(request: HttpServletRequest, response: HttpServletResponse, handler: Any?, exception: Throwable?): MutableIterable<Tag> =
            super.getTags(request, response, handler, exception).toMutableList().apply {
                when (val problemDetailsHeader = response.getHeader("problem-details")) {
                    null -> add(Tag.of("problem-details", "n/a"))
                    else -> {
                        add(Tag.of("problem-details", problemDetailsHeader))
                    }
                }
            }
}
