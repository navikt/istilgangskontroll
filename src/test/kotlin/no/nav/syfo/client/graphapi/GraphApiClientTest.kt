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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphApiClientTest {
    private val externalMockEnvironment = ExternalMockEnvironment()
    private val valkeyStore = mockk<ValkeyStore>(relaxed = true)
    private val mockHttpClient = getMockHttpClient(env = externalMockEnvironment.environment)

    private val adRoller = AdRoller(env = externalMockEnvironment.environment)

    private val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        valkeyStore = valkeyStore,
        httpClient = mockHttpClient,
    )

    private val graphApiClient = GraphApiClient(
        azureAdClient = azureAdClient,
        baseUrl = externalMockEnvironment.environment.clients.graphApiUrl,
        relevantSyfoRoller = adRoller.toList(),
        httpClient = mockHttpClient,
        valkeyStore = valkeyStore,
        adRoller = adRoller,
    )

    @BeforeEach
    fun beforeEach() {
        clearMocks(valkeyStore)
    }

    @Test
    fun `Returns syfo access and stores in cache`() {
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
        assertTrue(hasAccess)
        verify(exactly = 1) { valkeyStore.get(key = eq(cacheKey)) }
        verify(exactly = 1) {
            valkeyStore.setObject<List<GraphApiGroup>>(
                key = eq(cacheKey),
                value = any(),
                expireSeconds = eq(GraphApiClient.TWELVE_HOURS_IN_SECS),
            )
        }
    }

    @Test
    fun `Denies syfo access and does not store in cache`() {
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
        assertFalse(hasAccess)
        verify(exactly = 1) { valkeyStore.get(key = eq(cacheKey)) }
        verify(exactly = 0) {
            valkeyStore.setObject<List<GraphApiGroup>>(
                key = eq(cacheKey),
                value = any(),
                expireSeconds = eq(GraphApiClient.TWELVE_HOURS_IN_SECS),
            )
        }
    }
}
