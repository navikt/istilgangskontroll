package no.nav.syfo.tilgang

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class TilgangApiSpek : Spek({

    val objectMapper: ObjectMapper = configuredJacksonMapper()
    describe("Check veiledertilganger") {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment()
            val VALID_TOKEN_BUT_NO_SYFO_TILGANG = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT_NO_SYFO_ACCESS,
            )

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
            )

            describe("SYFO access") {
                it("Allows access to veileder with SYFO-tilgang") {
                    val validToken = generateJWT(
                        audience = externalMockEnvironment.environment.azure.appClientId,
                        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                        navIdent = UserConstants.VEILEDER_IDENT,
                    )

                    with(
                        handleRequest(HttpMethod.Get, "$tilgangApiBasePath/navident/syfo") {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_CALL_ID_HEADER, "123")
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val tilgang = objectMapper.readValue<Tilgang>(response.content!!)
                        tilgang.erGodkjent shouldBeEqualTo true
                    }
                }
                it("Forbids access to veileder without SYFO-tilgang") {
                    with(
                        handleRequest(HttpMethod.Get, "$tilgangApiBasePath/navident/syfo") {
                            addHeader(HttpHeaders.Authorization, bearerHeader(VALID_TOKEN_BUT_NO_SYFO_TILGANG))
                            addHeader(NAV_CALL_ID_HEADER, "123")
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        val tilgang = objectMapper.readValue<Tilgang>(response.content!!)
                        tilgang.erAvslatt shouldBeEqualTo true
                    }
                }
            }

            describe("Enhet access") {
                it("Allows access to veileder with correct enhet") {
                    val validToken = generateJWT(
                        audience = externalMockEnvironment.environment.azure.appClientId,
                        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                        navIdent = UserConstants.VEILEDER_IDENT,
                    )
                    val enhet = UserConstants.ENHET_VEILEDER

                    with(
                        handleRequest(HttpMethod.Get, "$tilgangApiBasePath/navident/enhet/$enhet") {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_CALL_ID_HEADER, "123")
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val tilgang = objectMapper.readValue<Tilgang>(response.content!!)
                        tilgang.erGodkjent shouldBeEqualTo true
                    }
                }
                it("Forbids access to veileder without correct enhet") {
                    val validToken = generateJWT(
                        audience = externalMockEnvironment.environment.azure.appClientId,
                        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                        navIdent = UserConstants.VEILEDER_IDENT,
                    )
                    val enhetWithoutTilgang = UserConstants.ENHET_VEILEDER_NO_ACCESS

                    with(
                        handleRequest(HttpMethod.Get, "$tilgangApiBasePath/navident/enhet/$enhetWithoutTilgang") {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_CALL_ID_HEADER, "123")
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        println("response: ${response.content}")
                        val tilgang = objectMapper.readValue<Tilgang>(response.content!!)
                        tilgang.erAvslatt shouldBeEqualTo true
                    }
                }

                it("Forbid access to veileder who has tilgang to enhet but not syfo") {
                    val enhet = UserConstants.ENHET_VEILEDER

                    with(
                        handleRequest(HttpMethod.Get, "$tilgangApiBasePath/navident/enhet/$enhet") {
                            addHeader(HttpHeaders.Authorization, bearerHeader(VALID_TOKEN_BUT_NO_SYFO_TILGANG))
                            addHeader(NAV_CALL_ID_HEADER, "123")
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        val tilgang = objectMapper.readValue<Tilgang>(response.content!!)
                        tilgang.erAvslatt shouldBeEqualTo true
                    }
                }
            }

            describe("Person access") {
                it("Allows access to person with SYFO access, correct local enhet, and no special permissions needed") {
                    val validToken = generateJWT(
                        audience = externalMockEnvironment.environment.azure.appClientId,
                        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                        navIdent = UserConstants.VEILEDER_IDENT,
                    )

                    with(
                        handleRequest(HttpMethod.Get, "$tilgangApiBasePath/navident/person") {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT)
                            addHeader(NAV_CALL_ID_HEADER, "123")
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val tilgang = objectMapper.readValue<Tilgang>(response.content!!)
                        tilgang.erGodkjent shouldBeEqualTo true
                    }
                }

                it("Forbid access to person if no SYFO access") {
                    val validToken = generateJWT(
                        audience = externalMockEnvironment.environment.azure.appClientId,
                        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                        navIdent = UserConstants.VEILEDER_IDENT_NO_SYFO_ACCESS,
                    )

                    with(
                        handleRequest(HttpMethod.Get, "$tilgangApiBasePath/navident/person") {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT)
                            addHeader(NAV_CALL_ID_HEADER, "123")
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        val tilgang = objectMapper.readValue<Tilgang>(response.content!!)
                        tilgang.erAvslatt shouldBeEqualTo true
                    }
                }

                it("Forbid access to person if no geografisk access") {
                    val validToken = generateJWT(
                        audience = externalMockEnvironment.environment.azure.appClientId,
                        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                        navIdent = UserConstants.VEILEDER_IDENT_NO_ENHET_ACCESS,
                    )

                    with(
                        handleRequest(HttpMethod.Get, "$tilgangApiBasePath/navident/person") {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT)
                            addHeader(NAV_CALL_ID_HEADER, "123")
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        val tilgang = objectMapper.readValue<Tilgang>(response.content!!)
                        tilgang.erAvslatt shouldBeEqualTo true
                    }
                }

                it("Forbid access to person if no access to skjermet person") {
                    val validToken = generateJWT(
                        audience = externalMockEnvironment.environment.azure.appClientId,
                        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                        navIdent = UserConstants.VEILEDER_IDENT,
                    )

                    with(
                        handleRequest(HttpMethod.Get, "$tilgangApiBasePath/navident/person") {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT_SKJERMET)
                            addHeader(NAV_CALL_ID_HEADER, "123")
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        val tilgang = objectMapper.readValue<Tilgang>(response.content!!)
                        tilgang.erAvslatt shouldBeEqualTo true
                    }
                }

                it("Forbid access to person if no access to adressebeskyttet person") {
                    val validToken = generateJWT(
                        audience = externalMockEnvironment.environment.azure.appClientId,
                        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                        navIdent = UserConstants.VEILEDER_IDENT,
                    )

                    with(
                        handleRequest(HttpMethod.Get, "$tilgangApiBasePath/navident/person") {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT_GRADERT)
                            addHeader(NAV_CALL_ID_HEADER, "123")
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        val tilgang = objectMapper.readValue<Tilgang>(response.content!!)
                        tilgang.erAvslatt shouldBeEqualTo true
                    }
                }
            }
        }
    }
})
