package no.nav.sifinnsynapi.utils

import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.sifinnsynapi.oppslag.Adressebeskyttelse
import org.springframework.cloud.contract.spec.internal.MediaTypes
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

fun stubForAktørId(aktørId: String, status: Int) {
    WireMock.stubFor(
        WireMock.get(WireMock.urlPathMatching("/k9-selvbetjening-oppslag-mock/meg.*"))
            .withHeader("Authorization", WireMock.matching(".*"))
            .withQueryParam("a", WireMock.equalTo("aktør_id"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(status)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        //language=json
                        """
                            {
                                "aktør_id": "$aktørId"
                            }
                        """.trimIndent()
                    )
            )
    )
}

fun stubSystemoppslagForHentBarn(
    ident: String,
    status: Int,
    adressebeskyttelse: List<Adressebeskyttelse> = emptyList(),
) {
    WireMock.stubFor(
        WireMock.post(WireMock.urlPathMatching("/k9-selvbetjening-oppslag-mock/system/hent-barn"))
            .withHeader("Authorization", WireMock.matching(".*"))
            .withHeader("X-K9-Ytelse", WireMock.equalTo("PLEIEPENGER_SYKT_BARN"))
            .withRequestBody(
                WireMock.equalToJson(
                    //language=json
                    """
                    {
                      "identer": [
                        "$ident"
                      ]
                    }
                """.trimIndent()
                )
            )
            .willReturn(
                WireMock.aResponse()
                    .withStatus(status)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        //language=json
                        """
                            [
                              {
                                "aktørId": {
                                  "value": "123456789"
                                },
                                "pdlBarn": {
                                    "fornavn": "OLA",
                                    "etternavn": "NORDMANN",
                                    "forkortetNavn": "OLA NORDMANN",
                                    "ident": {
                                      "value": "$ident"
                                    },
                                    "fødselsdato": "2012-02-24",
                                    "adressebeskyttelse": ${
                                  if (adressebeskyttelse.isEmpty()) {
                                    """
                                      []
                                    """.trimIndent()
                                  } else {
                                    adressebeskyttelse.map {
                                      """
                                        {
                                          "gradering": "${it.gradering}"
                                        }
                                        """.trimIndent()
                                        }
                                    }   
                                }
                              }
                            }
                          ] 
                        """.trimIndent()
                    )
            )
    )
}

fun stubStsToken(
    forventetStatus: HttpStatus,
    forventetToken: String = "default token",
    utgårOm: Int,
    prioritet: Int = 1,
) {
    WireMock.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/security-token-service/rest/v1/sts/token"))
            .withQueryParam("grant_type", WireMock.equalTo("client_credentials"))
            .withQueryParam("scope", WireMock.equalTo("openid"))
            .atPriority(prioritet)
            .willReturn(
                WireMock.aResponse()
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
                            """.trimIndent()
                    )
            )
    );
}
