package no.nav.sifinnsynapi.audit

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuditConfiguration(
    @Value("\${no.nav.audit.vendor}") private val auditVendor: String,
    @Value("\${no.nav.audit.product}") private val auditProduct: String
) {

    @Bean
    fun auditVendor(): String {
        return auditVendor
    }

    @Bean
    fun auditProduct(): String {
        return auditProduct
    }
}
