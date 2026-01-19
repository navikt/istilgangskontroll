package no.nav.syfo.tilgang

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ENHET_VEILEDER
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.configure
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TilgangApiTest {
    private val externalMockEnvironment = ExternalMockEnvironment()
    private val graphApiClient = mockk<GraphApiClient>(relaxed = true)
    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT,
    )
    private val validTokenNoSyfotilgang = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT_NO_SYFO_ACCESS,
    )
    private val validTokenNoEnhetAccess = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT_NO_ENHET_ACCESS,
    )
    private val validTokenWithoutPapirsykmeldingGroup = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT_NO_PAPIRSYKMELDING_ACCESS,
    )

    private val enhetWithoutTilgang = UserConstants.ENHET_VEILEDER_NO_ACCESS
    private val adRoller = AdRoller(externalMockEnvironment.environment)

    private fun ApplicationTestBuilder.setupApi(graphApiClient: GraphApiClient? = null): HttpClient {
        application {
            routing {
                application.testApiModule(
                    externalMockEnvironment = externalMockEnvironment,
                    graphApiClientMock = graphApiClient,
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

    @AfterEach
    fun afterEach() {
        clearMocks(
            graphApiClient,
        )
    }

    @Nested
    @DisplayName("SYFO access")
    inner class SyfoAccess {

        @Test
        fun `Allows access to veileder with SYFO-tilgang`() {
            testApplication {
                val graphApiClientMock = spyk(graphApiClient)
                coEvery {
                    graphApiClientMock.getGrupperForVeilederOgCache(any(), any())
                } returns listOf(createGruppeForRole(adRoller.SYFO))

                val client = setupApi(graphApiClientMock)
                val response = client.get("$tilgangApiBasePath/navident/syfo") {
                    bearerAuth(validToken)
                    header(NAV_CALL_ID_HEADER, "123")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }

                assertEquals(HttpStatusCode.OK, response.status)
                val tilgang = response.body<Tilgang>()
                assertTrue(tilgang.erGodkjent)
            }
        }

        @Test
        fun `Forbids access to veileder without SYFO-tilgang`() {
            testApplication {
                val client = setupApi()

                val response = client.get("$tilgangApiBasePath/navident/syfo") {
                    bearerAuth(validTokenNoSyfotilgang)
                    header(NAV_CALL_ID_HEADER, "123")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }

                assertEquals(HttpStatusCode.Forbidden, response.status)
                val tilgang = response.body<Tilgang>()
                assertTrue(tilgang.erAvslatt)
            }
        }
    }

    @Nested
    @DisplayName("Enhet access")
    inner class EnhetAccess {

        @Test
        fun `Allows access to veileder with correct enhet`() {
            testApplication {
                val graphApiClientMock = spyk(graphApiClient)
                coEvery {
                    graphApiClientMock.getGrupperForVeilederOgCache(any(), any())
                } returns listOf(
                    createGruppeForRole(adRoller.SYFO),
                    createGruppeForEnhet(ENHET_VEILEDER)
                )

                val client = setupApi(graphApiClientMock)
                val response = client.get("$tilgangApiBasePath/navident/enhet/$ENHET_VEILEDER") {
                    bearerAuth(validToken)
                    header(NAV_CALL_ID_HEADER, "123")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }

                assertEquals(HttpStatusCode.OK, response.status)
                val tilgang = response.body<Tilgang>()
                assertTrue(tilgang.erGodkjent)
            }
        }

        @Test
        fun `Forbids access to veileder without correct enhet`() {
            testApplication {
                val client = setupApi()

                val response = client.get("$tilgangApiBasePath/navident/enhet/$enhetWithoutTilgang") {
                    bearerAuth(validToken)
                    header(NAV_CALL_ID_HEADER, "123")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }

                assertEquals(HttpStatusCode.Forbidden, response.status)
                val tilgang = response.body<Tilgang>()
                assertTrue(tilgang.erAvslatt)
            }
        }

        @Test
        fun `Forbid access to veileder who has tilgang to enhet but not syfo`() {
            testApplication {
                val client = setupApi()

                val response = client.get("$tilgangApiBasePath/navident/enhet/$ENHET_VEILEDER") {
                    bearerAuth(validTokenNoSyfotilgang)
                    header(NAV_CALL_ID_HEADER, "123")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }

                assertEquals(HttpStatusCode.Forbidden, response.status)
                val tilgang = response.body<Tilgang>()
                assertTrue(tilgang.erAvslatt)
            }
        }
    }

    @Nested
    @DisplayName("Person access")
    inner class PersonAccess {

        @Test
        fun `Allows access to person with SYFO access, correct local enhet, and no special permissions needed`() {
            testApplication {
                val graphApiClientMock = spyk(graphApiClient)
                coEvery { graphApiClientMock.getGrupperForVeilederOgCache(any(), any()) } returns
                    listOf(
                        createGruppeForRole(adRoller.SYFO),
                        createGruppeForEnhet(ENHET_VEILEDER)
                    )
                val client = setupApi(graphApiClientMock)

                val response = client.get("$tilgangApiBasePath/navident/person") {
                    bearerAuth(validToken)
                    header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT)
                    header(NAV_CALL_ID_HEADER, "123")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }

                assertEquals(HttpStatusCode.OK, response.status)
                val tilgang = response.body<Tilgang>()
                assertTrue(tilgang.erGodkjent)
            }
        }

        @Test
        fun `Forbid access to person if no SYFO access`() {
            testApplication {
                val client = setupApi()

                val response = client.get("$tilgangApiBasePath/navident/person") {
                    bearerAuth(validTokenNoSyfotilgang)
                    header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT)
                    header(NAV_CALL_ID_HEADER, "123")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }

                assertEquals(HttpStatusCode.Forbidden, response.status)
                val tilgang = response.body<Tilgang>()
                assertTrue(tilgang.erAvslatt)
            }
        }

        @Test
        fun `Forbid access to person if no geografisk access`() {
            testApplication {
                val client = setupApi()

                val response = client.get("$tilgangApiBasePath/navident/person") {
                    bearerAuth(validTokenNoEnhetAccess)
                    header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT)
                    header(NAV_CALL_ID_HEADER, "123")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }

                assertEquals(HttpStatusCode.Forbidden, response.status)
                val tilgang = response.body<Tilgang>()
                assertTrue(tilgang.erAvslatt)
            }
        }

        @Test
        fun `Forbid access to person if no access to skjermet person`() {
            testApplication {
                val client = setupApi()

                val response = client.get("$tilgangApiBasePath/navident/person") {
                    bearerAuth(validTokenNoEnhetAccess)
                    header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT_SKJERMET)
                    header(NAV_CALL_ID_HEADER, "123")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }

                assertEquals(HttpStatusCode.Forbidden, response.status)
                val tilgang = response.body<Tilgang>()
                assertTrue(tilgang.erAvslatt)
            }
        }

        @Test
        fun `Forbid access to person if no access to adressebeskyttet person`() {
            testApplication {
                val client = setupApi()

                val response = client.get("$tilgangApiBasePath/navident/person") {
                    bearerAuth(validTokenNoEnhetAccess)
                    header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT_GRADERT)
                    header(NAV_CALL_ID_HEADER, "123")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }

                assertEquals(HttpStatusCode.Forbidden, response.status)
                val tilgang = response.body<Tilgang>()
                assertTrue(tilgang.erAvslatt)
            }
        }
    }

    @Nested
    @DisplayName("Papirsykmelding access")
    inner class PapirsykmeldingAccess {

        @Test
        fun `approve access for veileder with correct AD group for 'normal' person`() {
            testApplication {
                val graphApiClientMock = spyk(graphApiClient)
                coEvery { graphApiClientMock.getGrupperForVeilederOgCache(any(), any()) } returns listOf(
                    createGruppeForRole(adRoller.SYFO),
                    createGruppeForRole(adRoller.PAPIRSYKMELDING),
                    createGruppeForEnhet(ENHET_VEILEDER)
                )
                val client = setupApi(graphApiClientMock)
                val response = client.get("$tilgangApiBasePath/navident/person/papirsykmelding") {
                    bearerAuth(validToken)
                    header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT)
                    header(NAV_CALL_ID_HEADER, "123")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val tilgang = response.body<Tilgang>()
                assertTrue(tilgang.erGodkjent)
            }
        }

        @Test
        fun `deny access for veileder without correct AD group for 'normal' person`() {
            testApplication {
                val client = setupApi()

                val response = client.get("$tilgangApiBasePath/navident/person/papirsykmelding") {
                    bearerAuth(validTokenWithoutPapirsykmeldingGroup)
                    header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT)
                    header(NAV_CALL_ID_HEADER, "123")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }

                assertEquals(HttpStatusCode.Forbidden, response.status)
                val tilgang = response.body<Tilgang>()
                assertFalse(tilgang.erGodkjent)
            }
        }
    }

    @Nested
    @DisplayName("Preload cache")
    inner class PreloadCache {
        private val apiUrl = "$tilgangApiBasePath/system/preloadbrukere"
        private val requestBody = listOf(UserConstants.PERSONIDENT)
        private val validSystemToken = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            azp = syfooversiktsrvClientId,
        )

        @Test
        fun `return OK after loading cache`() {
            testApplication {
                val client = setupApi()

                val response = client.post(apiUrl) {
                    bearerAuth(validSystemToken)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header(NAV_CALL_ID_HEADER, "123")
                    setBody(requestBody)
                }

                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

        @Test
        fun `should return status Forbidden if wrong consumer azp`() {
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

                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }
    }
}
