package no.nav.sifinnsynapi.utils

import com.github.tomakehurst.wiremock.client.WireMock
import org.springframework.cloud.contract.spec.internal.MediaTypes
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

fun stubForAktørId(aktørId: String, status: Int) {
    WireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/k9-selvbetjening-oppslag-mock/meg.*"))
            .withHeader("Authorization", WireMock.matching(".*"))
            .withQueryParam("a", WireMock.equalTo("aktør_id"))
            .willReturn(WireMock.aResponse()
                    .withStatus(status)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                            //language=json
                            """
                            {
                                "aktør_id": "$aktørId"
                            }
                        """.trimIndent()
                    ))
    )
}

fun stubStsToken(forventetStatus: HttpStatus, forventetToken: String = "default token", utgårOm: Int, prioritet: Int = 1) {
    WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/security-token-service/rest/v1/sts/token"))
            .withQueryParam("grant_type", WireMock.equalTo("client_credentials"))
            .withQueryParam("scope", WireMock.equalTo("openid"))
            .atPriority(prioritet)
            .willReturn(WireMock.aResponse()
                    .withStatus(forventetStatus.value())
                    .withHeader("Content-Type", MediaTypes.APPLICATION_JSON)
                    .withBody(
                            //language=json
                            """
                                {
                                  "access_token": "$forventetToken",
                                  "token_type": "Bearer",
                                  "expires_in": $utgårOm
                                }
                            """.trimIndent())));
}
