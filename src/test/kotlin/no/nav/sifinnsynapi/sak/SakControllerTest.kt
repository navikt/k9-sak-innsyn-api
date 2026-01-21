package no.nav.sifinnsynapi.sak

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.k9.innsyn.sak.Aksjonspunkt
import no.nav.k9.innsyn.sak.BehandlingStatus
import no.nav.k9.innsyn.sak.FagsakYtelseType
import no.nav.k9.innsyn.sak.Saksnummer
import no.nav.k9.søknad.felles.type.Periode
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.sifinnsynapi.Routes
import no.nav.sifinnsynapi.config.Issuers
import no.nav.sifinnsynapi.config.SecurityConfiguration
import no.nav.sifinnsynapi.oppslag.Organisasjon
import no.nav.sifinnsynapi.util.CallIdGenerator
import no.nav.sifinnsynapi.utils.defaultEttersendelse
import no.nav.sifinnsynapi.utils.defaultSøknad
import no.nav.sifinnsynapi.utils.hentToken
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test.
@Import(CallIdGenerator::class, SecurityConfiguration::class)
@WebMvcTest(controllers = [SakController::class])
@ActiveProfiles("test")
class SakControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var mockOAuth2Server: MockOAuth2Server

    @MockkBean(relaxed = true)
    lateinit var sakService: SakService

    @BeforeAll
    internal fun setUp() {
        Assertions.assertNotNull(mockOAuth2Server)
    }

    @Test
    fun `internal server error gir 500 med forventet problem-details`() {
        every {
            sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        } throws Exception("Ooops, noe gikk galt...")

        //language=json
        val errorResponse =
            """{"type":"/problem-details/internal-server-error","title":"Et uventet feil har oppstått","status":500,"detail":"Ooops, noe gikk galt...","instance":"http://localhost/saker"}""".trimIndent()

        val token = mockOAuth2Server.hentToken().serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(Routes.SAKER, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isInternalServerError)
            .andExpect(MockMvcResultMatchers.header().exists("problem-details"))
            .andExpect(MockMvcResultMatchers.content().json(errorResponse))
            .andExpect {
                val actualHeader = it.response.getHeader("problem-details")
                JSONAssert.assertEquals(errorResponse, actualHeader, false)
            }
    }

    @Test
    fun `Gitt 200 respons, forvent korrekt format på liste av saker med tokenx token`() {
        val søknadId = UUID.fromString("e9514b88-ace4-4faa-b894-a9ef66b53e79")
        val mottattDato = ZonedDateTime.parse("2024-02-06T14:50:24.318Z")
        val tidsfrist = ZonedDateTime.parse("2024-02-05T14:50:24.318Z")
        val status = BehandlingStatus.OPPRETTET
        val aksjonspunkter = listOf(
            AksjonspunktDTO(
                venteårsak = Aksjonspunkt.Venteårsak.INNTEKTSMELDING,
                tidsfrist = tidsfrist
            )
        )
        val saksbehandlingsFrist = LocalDate.parse("2024-01-01")
        every {
            sakService.hentSaker(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        } returns listOf(
            PleietrengendeMedSak(
                pleietrengende = PleietrengendeDTO(
                    fødselsdato = LocalDate.parse("2000-01-01"),
                    fornavn = "Ola",
                    mellomnavn = null,
                    etternavn = "Nordmann",
                    aktørId = "11111111111",
                    identitetsnummer = "1234567890"
                ),
                sak = SakDTO(
                    saksnummer = Saksnummer("ABC123"),
                    utledetStatus = UtledetStatus(
                        status = status,
                        aksjonspunkter = aksjonspunkter,
                        saksbehandlingsFrist = saksbehandlingsFrist
                    ),
                    saksbehandlingsFrist = saksbehandlingsFrist,
                    fagsakYtelseType = no.nav.k9.kodeverk.behandling.FagsakYtelseType.fraKode(FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode),
                    ytelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                    behandlinger = listOf(
                        BehandlingDTO(
                            status = status,
                            opprettetTidspunkt = LocalDate.parse("2024-02-06").atStartOfDay(ZoneId.of("UTC")),
                            innsendelser = listOf(
                                InnsendelserISakDTO(
                                    søknadId = søknadId,
                                    innsendelsestype = Innsendelsestype.SØKNAD,
                                    k9FormatInnsendelse = defaultSøknad(
                                        søknadId = søknadId,
                                        søknadsPeriode = Periode("2024-01-01/2024-01-31"),
                                        søkersIdentitetsnummer = "1234567890",
                                        arbeidstid = null,
                                        tilsynsordning = null,
                                        mottattDato = mottattDato
                                    ),
                                    dokumenter = listOf(
                                        DokumentDTO(
                                            journalpostId = "123456789",
                                            dokumentInfoId = "123456789",
                                            saksnummer = Saksnummer("ABC123"),
                                            tittel = "Søknad om pleiepenger",
                                            dokumentType = DokumentBrevkode.PLEIEPENGER_SYKT_BARN_SOKNAD,
                                            filtype = "PDFA",
                                            harTilgang = true,
                                            url = URI("http://localhost:8080/saker/123456789").toURL(),
                                            journalposttype = Journalposttype.INNGÅENDE,
                                            relevanteDatoer = listOf(
                                                RelevantDatoDTO(
                                                    dato = mottattDato.toString(),
                                                    datotype = Datotype.DATO_OPPRETTET
                                                )
                                            )
                                        )
                                    ),
                                    arbeidsgivere = listOf(
                                        Organisasjon(
                                            organisasjonsnummer = "123456789",
                                            navn = "Arbeidsgiver AS"
                                        )
                                    ),
                                    mottattTidspunkt = mottattDato
                                ),
                                InnsendelserISakDTO(
                                    søknadId = søknadId,
                                    innsendelsestype = Innsendelsestype.ETTERSENDELSE,
                                    k9FormatInnsendelse = defaultEttersendelse(
                                        søknadId = søknadId,
                                        søkersIdentitetsnummer = "1234567890",
                                        pleietrengendeIdentitetsnummer = "21121879023",
                                        mottattDato = mottattDato
                                    ),
                                    dokumenter = listOf(
                                        DokumentDTO(
                                            journalpostId = "123456789",
                                            dokumentInfoId = "123456789",
                                            saksnummer = Saksnummer("ABC123"),
                                            tittel = "Ettersendelse for Søknad om pleiepenger",
                                            dokumentType = DokumentBrevkode.PLEIEPENGER_SYKT_BARN_ETTERSENDELSE,
                                            filtype = "PDFA",
                                            harTilgang = true,
                                            url = URI("http://localhost:8080/saker/123456789").toURL(),
                                            journalposttype = Journalposttype.INNGÅENDE,
                                            relevanteDatoer = listOf(
                                                RelevantDatoDTO(
                                                    dato = mottattDato.toString(),
                                                    datotype = Datotype.DATO_OPPRETTET
                                                )
                                            )
                                        )
                                    ),
                                    arbeidsgivere = null,
                                    mottattTidspunkt = mottattDato
                                ),
                            ),
                            aksjonspunkter = aksjonspunkter,
                            utgåendeDokumenter = listOf(
                                DokumentDTO(
                                    journalpostId = "123456789",
                                    dokumentInfoId = "123456789",
                                    saksnummer = Saksnummer("ABC123"),
                                    tittel = "Etterlysning av inntektsmelding",
                                    dokumentType = DokumentBrevkode.ETTERLYST_INNTEKTSMELDING,
                                    filtype = "PDFA",
                                    harTilgang = true,
                                    url = URI("http://localhost:8080/saker/123456789").toURL(),
                                    journalposttype = Journalposttype.UTGÅENDE,
                                    relevanteDatoer = listOf(
                                        RelevantDatoDTO(
                                            dato = mottattDato.toString(),
                                            datotype = Datotype.DATO_OPPRETTET
                                        )
                                    )
                                )
                            ),
                        )
                    )
                )
            )
        )

        val token = mockOAuth2Server.hentToken().serialize()

        // Husk å oppdatere no.nav.sifinnsynapi.config.SwaggerConfiguration.SAKER_RESPONSE_EKSEMPEL ved endringer.
        //language=json
        val forventetJsonResponse = """
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
                          "utledetStatus": {
                            "status": "OPPRETTET",
                            "aksjonspunkter": [
                              {
                                "venteårsak": "INNTEKTSMELDING",
                                "tidsfrist": "$tidsfrist"
                              }
                            ],
                            "saksbehandlingsFrist": "2024-01-01"
                          },
                          "saksbehandlingsFrist": "2024-01-01",
                          "fagsakYtelseType": {
                            "kode": "PSB",
                            "kodeverk": "FAGSAK_YTELSE"
                          },
                          "ytelseType": "PSB",
                          "behandlinger": [
                            {
                              "status": "OPPRETTET",
                              "opprettetTidspunkt": "2024-02-06T00:00:00.000Z",
                              "avsluttetTidspunkt": null,
                              "innsendelser": [
                                {
                                  "søknadId": "$søknadId",
                                  "mottattTidspunkt": "$mottattDato",
                                  "innsendelsestype": "SØKNAD",
                                  "arbeidsgivere": [
                                    {
                                      "organisasjonsnummer": "123456789",
                                      "navn": "Arbeidsgiver AS"
                                    }
                                  ],
                                  "k9FormatInnsendelse": {
                                    "søknadId": "$søknadId",
                                    "versjon": "1.0.0",
                                    "mottattDato": "$mottattDato",
                                    "søker": {
                                      "norskIdentitetsnummer": "1234567890"
                                    },
                                    "ytelse": {
                                      "type": "PLEIEPENGER_SYKT_BARN",
                                      "barn": {
                                        "norskIdentitetsnummer": "21121879023",
                                        "fødselsdato": null
                                      },
                                      "erSammenMedBarnet": null,
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
                                      "dokumentType": "PLEIEPENGER_SYKT_BARN_SOKNAD",
                                      "filtype": "PDFA",
                                      "harTilgang": true,
                                      "url": "http://localhost:8080/saker/123456789",
                                      "relevanteDatoer": [
                                        {
                                          "dato": "$mottattDato",
                                          "datotype": "DATO_OPPRETTET"
                                        }
                                      ]
                                    }
                                  ]
                                },
                                {
                                  "søknadId": "e9514b88-ace4-4faa-b894-a9ef66b53e79",
                                  "mottattTidspunkt": "2024-02-06T14:50:24.318Z",
                                  "innsendelsestype": "ETTERSENDELSE",
                                  "k9FormatInnsendelse": {
                                    "søknadId": "e9514b88-ace4-4faa-b894-a9ef66b53e79",
                                    "versjon": "0.0.1",
                                    "mottattDato": "2024-02-06T14:50:24.318Z",
                                    "søker": {
                                      "norskIdentitetsnummer": "1234567890"
                                    },
                                    "ytelse": "PLEIEPENGER_SYKT_BARN",
                                    "pleietrengende": {
                                      "norskIdentitetsnummer": "21121879023"
                                    },
                                    "type": "LEGEERKLÆRING"
                                  },
                                  "dokumenter": [
                                    {
                                      "journalpostId": "123456789",
                                      "dokumentInfoId": "123456789",
                                      "saksnummer": "ABC123",
                                      "tittel": "Ettersendelse for Søknad om pleiepenger",
                                      "dokumentType": "PLEIEPENGER_SYKT_BARN_ETTERSENDELSE",
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
                                  ],
                                  "arbeidsgivere": null
                                }
                              ],
                              "utgåendeDokumenter": [
                                {
                                  "journalpostId": "123456789",
                                  "dokumentInfoId": "123456789",
                                  "saksnummer": "ABC123",
                                  "tittel": "Etterlysning av inntektsmelding",
                                  "dokumentType": "ETTERLYST_INNTEKTSMELDING",
                                  "filtype": "PDFA",
                                  "harTilgang": true,
                                  "url": "http://localhost:8080/saker/123456789",
                                  "relevanteDatoer": [
                                    {
                                      "dato": "$mottattDato",
                                      "datotype": "DATO_OPPRETTET"
                                    }
                                  ]
                                }
                              ],
                              "aksjonspunkter": [
                                {
                                  "venteårsak": "INNTEKTSMELDING",
                                  "tidsfrist": "$tidsfrist"
                                }
                              ]
                            }
                          ]
                        }
                      }
                    ]
                """.trimIndent()
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(Routes.SAKER, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json(forventetJsonResponse, JsonCompareMode.STRICT))
    }

    @Test
    fun `Ved henting av generell saksbehandlingstid uten token, forvent 8 uker`() {
        every {
            sakService.hentGenerellSaksbehandlingstid()
        } returns SaksbehandlingtidDTO(saksbehandlingstidUker = 8)

        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode("${Routes.SAKER}/saksbehandlingstid", Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.saksbehandlingstidUker").value(8)
            )
    }

    @ParameterizedTest
    @ValueSource(strings = [Issuers.AZURE, "ukjent"])
    fun `gitt request med token utsedt av annen issuer enn idporten eller tokenx, forevnt 401`(issuer: String) {
        val token = mockOAuth2Server.hentToken(issuerId = issuer, claims = mapOf()).serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(Routes.SAKER, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(401))
            .andExpect(MockMvcResultMatchers.jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    fun `gitt request uten token, forevnt 401`() {
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(Routes.SAKER, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(401))
            .andExpect(MockMvcResultMatchers.jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    fun `gitt request med token med ukjent audience, forevnt 401`() {
        val token = mockOAuth2Server.hentToken(audience = "ukjent audience").serialize()
        mockMvc.perform(
            MockMvcRequestBuilders
                .get(URI(URLDecoder.decode(Routes.SAKER, Charset.defaultCharset())))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("$.type").value("/problem-details/uautentisert-forespørsel"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Ikke autentisert"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(401))
            .andExpect(MockMvcResultMatchers.jsonPath("$.stackTrace").doesNotExist())
    }
}
