package no.nav.sifinnsynapi.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.OAuthFlow
import io.swagger.v3.oas.models.security.OAuthFlows
import io.swagger.v3.oas.models.security.Scopes
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.EnvironmentAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders


@Configuration
class SwaggerConfiguration(
    @Value("\${springdoc.oAuthFlow.authorizationUrl}") val authorizationUrl: String,
    @Value("\${springdoc.oAuthFlow.tokenUrl}") val tokenUrl: String,
    @Value("\${springdoc.oAuthFlow.apiScope}") val apiScope: String,
) : EnvironmentAware {
    private var env: Environment? = null

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("K9 Sak Innsyn Api")
                    .description("API spesifikasjon for k9-sak-innsyn-api")
                    .version("v1.0.0")
            )
            .externalDocs(
                ExternalDocumentation()
                    .description("K9 Sak Innsyn Api GitHub repository")
                    .url("https://github.com/navikt/k9-sak-innsyn-api")
            )
            .components(
                Components()
                    .addSecuritySchemes("Authorization", tokenXApiToken())
                    .addSecuritySchemes("oauth2", azureLogin())
            )
            .addSecurityItem(
                SecurityRequirement()
                    .addList("oauth2", listOf("read", "write"))
                    .addList("Authorization")
            )
    }

    private fun azureLogin(): SecurityScheme {
        return SecurityScheme()
            .name("oauth2")
            .type(SecurityScheme.Type.OAUTH2)
            .scheme("oauth2")
            .`in`(SecurityScheme.In.HEADER)
            .flows(
                OAuthFlows()
                    .authorizationCode(
                        OAuthFlow().authorizationUrl(authorizationUrl)
                            .tokenUrl(tokenUrl)
                            .scopes(Scopes().addString(apiScope, "read,write"))
                    )
            )
    }

    private fun tokenXApiToken(): SecurityScheme {
        val authorization_endpoint = "https://tokenx.dev-gcp.nav.cloud.nais.io/authorization"
        val token_endpoint = "https://tokenx.dev-gcp.nav.cloud.nais.io/token"
        val audience = "dev-gcp:dusseldorf:k9-sak-innsyn-api"

        return SecurityScheme()
            .name("tokenx")
            .type(SecurityScheme.Type.OAUTH2)
            .scheme("oauth2")
            .`in`(SecurityScheme.In.HEADER)
            .flows(
                OAuthFlows()
                    .authorizationCode(
                        OAuthFlow()
                            .authorizationUrl(authorization_endpoint)
                            .tokenUrl(token_endpoint)
                            .scopes(Scopes().addString(audience, "read,write"))
                    )
            )

        /*return SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .name(HttpHeaders.AUTHORIZATION)
            .scheme("bearer")
            .bearerFormat("JWT")
            .`in`(SecurityScheme.In.HEADER)
            .description(
                """Eksempel på verdi som skal inn i Value-feltet (Bearer trengs altså ikke å oppgis): 'eyAidH...'
                For nytt token -> https://tokenx-token-generator.intern.dev.nav.no/api/obo?aud=dev-gcp:dusseldorf:k9-sak-innsyn-api
            """.trimMargin()
            )*/
    }

    override fun setEnvironment(env: Environment) {
        this.env = env
    }

    companion object {
        // language=json
        const val SAKER_RESPONSE_EKSEMPEL = """
                    [
                      {
                        "pleietrengende": {
                          "fødselsdato": "2000-01-01",
                          "fornavn": "Ola",
                          "mellomnavn": null,
                          "etternavn": "Nordmann",
                          "aktørId": "11111111111",
                          "identitetsnummer": "1234567890"
                        },
                        "sak": {
                          "saksnummer": "ABC123",
                          "saksbehandlingsFrist": "2024-01-01",
                          "fagsakYtelseType": {
                            "kode": "PSB",
                            "kodeverk": "FAGSAK_YTELSE"
                          },
                          "behandlinger": [
                            {
                              "status": "OPPRETTET",
                              "opprettetDato": "2024-02-06",
                              "avsluttetDato": null,
                              "søknader": [
                                {
                                  "kildesystem": "søknadsdialog",
                                  "k9FormatSøknad": {
                                    "søknadId": "10ed495f-83f2-46c1-a7bb-58d55fd1b1b2",
                                    "versjon": "1.0.0",
                                    "mottattDato": "2024-02-06T14:50:24.318Z",
                                    "søker": {
                                      "norskIdentitetsnummer": "1234567890"
                                    },
                                    "ytelse": {
                                      "type": "PLEIEPENGER_SYKT_BARN",
                                      "barn": {
                                        "norskIdentitetsnummer": "21121879023",
                                        "fødselsdato": null
                                      },
                                      "søknadsperiode": [
                                        "2024-01-01/2024-01-31"
                                      ],
                                      "endringsperiode": [],
                                      "trekkKravPerioder": [],
                                      "opptjeningAktivitet": {},
                                      "dataBruktTilUtledning": null,
                                      "annetDataBruktTilUtledning": null,
                                      "infoFraPunsj": null,
                                      "bosteder": {
                                        "perioder": {},
                                        "perioderSomSkalSlettes": {}
                                      },
                                      "utenlandsopphold": {
                                        "perioder": {},
                                        "perioderSomSkalSlettes": {}
                                      },
                                      "beredskap": {
                                        "perioder": {},
                                        "perioderSomSkalSlettes": {}
                                      },
                                      "nattevåk": {
                                        "perioder": {},
                                        "perioderSomSkalSlettes": {}
                                      },
                                      "tilsynsordning": {
                                        "perioder": {}
                                      },
                                      "lovbestemtFerie": {
                                        "perioder": {}
                                      },
                                      "arbeidstid": {
                                        "arbeidstakerList": [],
                                        "frilanserArbeidstidInfo": null,
                                        "selvstendigNæringsdrivendeArbeidstidInfo": null
                                      },
                                      "uttak": {
                                        "perioder": {}
                                      },
                                      "omsorg": {
                                        "relasjonTilBarnet": null,
                                        "beskrivelseAvOmsorgsrollen": null
                                      }
                                    },
                                    "språk": "nb",
                                    "journalposter": [
                                      {
                                        "inneholderInfomasjonSomIkkeKanPunsjes": null,
                                        "inneholderInformasjonSomIkkeKanPunsjes": null,
                                        "inneholderMedisinskeOpplysninger": null,
                                        "journalpostId": "123456789"
                                      }
                                    ],
                                    "begrunnelseForInnsending": {
                                      "tekst": null
                                    },
                                    "kildesystem": null
                                  },
                                  "dokumenter": [
                                    {
                                      "journalpostId": "123456789",
                                      "dokumentInfoId": "123456789",
                                      "saksnummer": "ABC123",
                                      "tittel": "Søknad om pleiepenger",
                                      "filtype": "PDFA",
                                      "harTilgang": true,
                                      "url": "http://localhost:8080/saker/123456789",
                                      "relevanteDatoer": [
                                        {
                                          "dato": "2024-02-06T14:50:24.318Z",
                                          "datotype": "DATO_OPPRETTET"
                                        }
                                      ]
                                    }
                                  ]
                                }
                              ],
                              "aksjonspunkter": [
                                {
                                  "venteårsak": "INNTEKTSMELDING"
                                }
                              ]
                            }
                          ]
                        }
                      }
                    ]
                """
    }
}
