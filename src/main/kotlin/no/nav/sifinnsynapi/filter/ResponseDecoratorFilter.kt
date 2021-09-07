package no.nav.sifinnsynapi.filter

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class ResponseDecoratorFilter : Filter {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ResponseDecoratorFilter::class.java)
    }

    /**
     * The `doFilter` method of the Filter is called by the container each time a request/response pair is
     * passed through the chain due to a client request for a resource at the end of the chain. The FilterChain passed
     * in to this method allows the Filter to pass on the request and response to the next entity in the chain.
     *
     *
     *
     * A typical implementation of this method would follow the following pattern:
     *
     *  1. Examine the request
     *  1. Optionally wrap the request object with a custom implementation to filter content or headers for input
     * filtering
     *  1. Optionally wrap the response object with a custom implementation to filter content or headers for output
     * filtering
     *  1.
     *
     *  * **Either** invoke the next entity in the chain using the FilterChain object
     * (`chain.doFilter()`),
     *  * **or** not pass on the request/response pair to the next entity in the filter chain to block the
     * request processing
     *
     *  1. Directly set headers on the response after invocation of the next entity in the filter chain.
     *
     *
     * @param request  the `ServletRequest` object contains the client's request
     * @param response the `ServletResponse` object contains the filter's response
     * @param chain    the `FilterChain` for invoking the next filter or the resource
     * @throws IOException      if an I/O related error has occurred during the processing
     * @throws ServletException if an exception occurs that interferes with the filter's normal operation
     *
     * @see UnavailableException
     */
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val requestWrapper = ContentCachingRequestWrapper(request as HttpServletRequest)
        val responseWrapper = ContentCachingResponseWrapper(response as HttpServletResponse)
        chain.doFilter(requestWrapper, responseWrapper)

        when (response.status) {
            200, 201, 202, 204 -> {}
            else -> {
                val content = String(responseWrapper.contentAsByteArray, Charsets.UTF_8)
                response.addHeader("problem-details", content)
            }
        }
        responseWrapper.copyBodyToResponse()
    }
}
