package no.nav.syfo.tilgang

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class TilgangApiSpek : Spek({

    val objectMapper: ObjectMapper = configuredJacksonMapper()
    describe("Check veiledertilganger") {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment()

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
            )

            it("Allows access to veileder with SYFO-tilgang") {
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.azure.appClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                    navIdent = UserConstants.VEILEDER_IDENT,
                )

                with(
                    handleRequest(HttpMethod.Get, "$tilgangApiBasePath/navident/syfo") {
                        addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val tilgang = objectMapper.readValue<Tilgang>(response.content!!)
                    tilgang.harTilgang shouldBeEqualTo true
                }
            }
            it("Forbids access to veileder without SYFO-tilgang") {
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.azure.appClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                    navIdent = UserConstants.VEILEDER_IDENT_NO_SYFO_ACCESS,
                )

                with(
                    handleRequest(HttpMethod.Get, "$tilgangApiBasePath/navident/syfo") {
                        addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                    println("response: ${response.content}")
                    val tilgang = objectMapper.readValue<Tilgang>(response.content!!)
                    tilgang.harTilgang shouldBeEqualTo false
                }
            }
        }
    }
})
