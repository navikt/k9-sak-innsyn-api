package no.nav.sifinnsynapi.config

import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import org.springframework.context.annotation.Configuration

@Configuration
@EnableOAuth2Client
class TokenClientConfig
