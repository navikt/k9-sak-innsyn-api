package no.nav.sifinnsynapi.util

import org.slf4j.Logger
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor

object RestTemplateUtils {
    fun requestLoggerInterceptor(logger: Logger) =
        ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            logger.info("HTTP Request: {} {}", request.method, request.uri)
            val response = execution.execute(request, body)
            logger.info("HTTP Response: {} {} {}", response.statusCode, request.method, request.uri)
            response
        }
}
