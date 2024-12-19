package no.nav.syfo.tilgang

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class TilgangApiSpek : Spek({
    describe("Check veiledertilganger") {
        val externalMockEnvironment = ExternalMockEnvironment()
        val validToken = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT,
        )
        val validTokenNoSyfotilgang = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT_NO_SYFO_ACCESS,
        )
        val validTokenNoEnhetAccess = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT_NO_ENHET_ACCESS,
        )
        val validTokenWithoutPapirsykmeldingGroup = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT_NO_PAPIRSYKMELDING_ACCESS,
        )

        val enhet = UserConstants.ENHET_VEILEDER
        val enhetWithoutTilgang = UserConstants.ENHET_VEILEDER_NO_ACCESS

        fun ApplicationTestBuilder.setupApi(): HttpClient {
            application {
                routing {
                    application.testApiModule(
                        externalMockEnvironment = externalMockEnvironment,
                    )
                }
            }
            val client = createClient {
                install(ContentNegotiation) {
                    jackson { configure() }
                }
            }
            return client
        }

        describe("SYFO access") {
            it("Allows access to veileder with SYFO-tilgang") {
                testApplication {
                    val client = setupApi()
                    val response = client.get("$tilgangApiBasePath/navident/syfo") {
                        bearerAuth(validToken)
                        header(NAV_CALL_ID_HEADER, "123")
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    response.status shouldBeEqualTo HttpStatusCode.OK
                    val tilgang = response.body<Tilgang>()
                    tilgang.erGodkjent shouldBeEqualTo true
                }
            }
            it("Forbids access to veileder without SYFO-tilgang") {
                testApplication {
                    val client = setupApi()
                    val response = client.get("$tilgangApiBasePath/navident/syfo") {
                        bearerAuth(validTokenNoSyfotilgang)
                        header(NAV_CALL_ID_HEADER, "123")
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    val tilgang = response.body<Tilgang>()
                    tilgang.erAvslatt shouldBeEqualTo true
                }
            }
        }

        describe("Enhet access") {
            it("Allows access to veileder with correct enhet") {
                testApplication {
                    val client = setupApi()
                    val response = client.get("$tilgangApiBasePath/navident/enhet/$enhet") {
                        bearerAuth(validToken)
                        header(NAV_CALL_ID_HEADER, "123")
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    response.status shouldBeEqualTo HttpStatusCode.OK
                    val tilgang = response.body<Tilgang>()
                    tilgang.erGodkjent shouldBeEqualTo true
                }
            }
            it("Forbids access to veileder without correct enhet") {
                testApplication {
                    val client = setupApi()
                    val response = client.get("$tilgangApiBasePath/navident/enhet/$enhetWithoutTilgang") {
                        bearerAuth(validToken)
                        header(NAV_CALL_ID_HEADER, "123")
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    val tilgang = response.body<Tilgang>()
                    tilgang.erAvslatt shouldBeEqualTo true
                }
            }

            it("Forbid access to veileder who has tilgang to enhet but not syfo") {
                testApplication {
                    val client = setupApi()
                    val response = client.get("$tilgangApiBasePath/navident/enhet/$enhet") {
                        bearerAuth(validTokenNoSyfotilgang)
                        header(NAV_CALL_ID_HEADER, "123")
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    val tilgang = response.body<Tilgang>()
                    tilgang.erAvslatt shouldBeEqualTo true
                }
            }
        }

        describe("Person access") {
            it("Allows access to person with SYFO access, correct local enhet, and no special permissions needed") {
                testApplication {
                    val client = setupApi()
                    val response = client.get("$tilgangApiBasePath/navident/person") {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT)
                        header(NAV_CALL_ID_HEADER, "123")
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    response.status shouldBeEqualTo HttpStatusCode.OK
                    val tilgang = response.body<Tilgang>()
                    tilgang.erGodkjent shouldBeEqualTo true
                }
            }

            it("Forbid access to person if no SYFO access") {
                testApplication {
                    val client = setupApi()
                    val response = client.get("$tilgangApiBasePath/navident/person") {
                        bearerAuth(validTokenNoSyfotilgang)
                        header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT)
                        header(NAV_CALL_ID_HEADER, "123")
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    val tilgang = response.body<Tilgang>()
                    tilgang.erAvslatt shouldBeEqualTo true
                }
            }

            it("Forbid access to person if no geografisk access") {
                testApplication {
                    val client = setupApi()
                    val response = client.get("$tilgangApiBasePath/navident/person") {
                        bearerAuth(validTokenNoEnhetAccess)
                        header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT)
                        header(NAV_CALL_ID_HEADER, "123")
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    val tilgang = response.body<Tilgang>()
                    tilgang.erAvslatt shouldBeEqualTo true
                }
            }

            it("Forbid access to person if no access to skjermet person") {
                testApplication {
                    val client = setupApi()
                    val response = client.get("$tilgangApiBasePath/navident/person") {
                        bearerAuth(validTokenNoEnhetAccess)
                        header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT_SKJERMET)
                        header(NAV_CALL_ID_HEADER, "123")
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    val tilgang = response.body<Tilgang>()
                    tilgang.erAvslatt shouldBeEqualTo true
                }
            }

            it("Forbid access to person if no access to adressebeskyttet person") {
                testApplication {
                    val client = setupApi()
                    val response = client.get("$tilgangApiBasePath/navident/person") {
                        bearerAuth(validTokenNoEnhetAccess)
                        header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT_GRADERT)
                        header(NAV_CALL_ID_HEADER, "123")
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    val tilgang = response.body<Tilgang>()
                    tilgang.erAvslatt shouldBeEqualTo true
                }
            }
        }

        describe("papirsykmelding access") {
            it("approve access for veileder with correct AD group for 'normal' person") {
                testApplication {
                    val client = setupApi()
                    val response = client.get("$tilgangApiBasePath/navident/person/papirsykmelding") {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT)
                        header(NAV_CALL_ID_HEADER, "123")
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    response.status shouldBeEqualTo HttpStatusCode.OK
                    val tilgang = response.body<Tilgang>()
                    tilgang.erGodkjent shouldBeEqualTo true
                }
            }

            it("deny access for veileder without correct AD group for 'normal' person") {
                testApplication {
                    val client = setupApi()
                    val response = client.get("$tilgangApiBasePath/navident/person/papirsykmelding") {
                        bearerAuth(validTokenWithoutPapirsykmeldingGroup)
                        header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT)
                        header(NAV_CALL_ID_HEADER, "123")
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    val tilgang = response.body<Tilgang>()
                    tilgang.erGodkjent shouldBeEqualTo false
                }
            }
        }

        describe("preload cache") {
            val apiUrl = "$tilgangApiBasePath/system/preloadbrukere"
            val requestBody = listOf(UserConstants.PERSONIDENT)
            val validSystemToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                azp = syfooversiktsrvClientId,
            )

            it("return OK after loading cache") {
                testApplication {
                    val client = setupApi()
                    val response = client.post(apiUrl) {
                        bearerAuth(validSystemToken)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        header(NAV_CALL_ID_HEADER, "123")
                        setBody(requestBody)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.OK
                }
            }
            it("should return status Forbidden if wrong consumer azp") {
                val invalidSystemToken = generateJWT(
                    audience = externalMockEnvironment.environment.azure.appClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                    azp = "invalid-consumer-azp",
                )
                testApplication {
                    val client = setupApi()
                    val response = client.post(apiUrl) {
                        bearerAuth(invalidSystemToken)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        header(NAV_CALL_ID_HEADER, "123")
                        setBody(requestBody)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.Forbidden
                }
            }
        }
    }
})
