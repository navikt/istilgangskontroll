package no.nav.syfo.client.graphapi

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.mocks.getMockHttpClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generateJWT
import no.nav.syfo.tilgang.AdRoller
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class GraphApiClientSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment()
    val valkeyStore = mockk<ValkeyStore>(relaxed = true)
    val mockHttpClient = getMockHttpClient(env = externalMockEnvironment.environment)

    val adRoller = AdRoller(env = externalMockEnvironment.environment)

    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        valkeyStore = valkeyStore,
        httpClient = mockHttpClient,
    )

    val graphApiClient = GraphApiClient(
        azureAdClient = azureAdClient,
        baseUrl = externalMockEnvironment.environment.clients.graphApiUrl,
        relevantSyfoRoller = adRoller.toList(),
        httpClient = mockHttpClient,
        valkeyStore = valkeyStore,
    )

    describe("GraphApiClient") {
        beforeEachTest { clearMocks(valkeyStore) }
        describe("SYFO access") {
            it("Returns syfo access and stores in cache") {
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.azure.appClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                    navIdent = UserConstants.VEILEDER_IDENT,
                )
                val cacheKey = "${GraphApiClient.GRAPHAPI_CACHE_KEY}-${UserConstants.VEILEDER_IDENT}"
                every {
                    valkeyStore.getListObject<GraphApiGroup>(cacheKey)
                } returns null
                every {
                    valkeyStore.get(any<String>())
                } returns null

                val hasAccess = runBlocking {
                    graphApiClient.hasAccess(
                        adRolle = adRoller.SYFO,
                        token = Token(validToken),
                        callId = UUID.randomUUID().toString(),
                    )
                }
                hasAccess shouldBeEqualTo true
                verify(exactly = 1) { valkeyStore.get(key = eq(cacheKey)) }
                verify(exactly = 1) {
                    valkeyStore.setObject<List<GraphApiGroup>>(
                        key = eq(cacheKey),
                        value = any(),
                        expireSeconds = eq(GraphApiClient.TWELVE_HOURS_IN_SECS),
                    )
                }
            }
            it("Denies syfo access and stores in cache") {
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.azure.appClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                    navIdent = UserConstants.VEILEDER_IDENT_NO_SYFO_ACCESS,
                )
                val cacheKey = "${GraphApiClient.GRAPHAPI_CACHE_KEY}-${UserConstants.VEILEDER_IDENT_NO_SYFO_ACCESS}"
                every {
                    valkeyStore.getListObject<GraphApiGroup>(cacheKey)
                } returns null
                every {
                    valkeyStore.get(any<String>())
                } returns null

                val hasAccess = runBlocking {
                    graphApiClient.hasAccess(
                        adRolle = adRoller.SYFO,
                        token = Token(validToken),
                        callId = UUID.randomUUID().toString(),
                    )
                }
                hasAccess shouldBeEqualTo false
                verify(exactly = 1) { valkeyStore.get(key = eq(cacheKey)) }
                verify(exactly = 1) {
                    valkeyStore.setObject<List<GraphApiGroup>>(
                        key = eq(cacheKey),
                        value = any(),
                        expireSeconds = eq(GraphApiClient.TWELVE_HOURS_IN_SECS),
                    )
                }
            }
        }
    }
})
