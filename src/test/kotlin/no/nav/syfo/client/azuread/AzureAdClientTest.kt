package no.nav.syfo.client.azuread

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.mocks.getMockHttpClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generateJWT
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AzureAdClientTest {
    private val externalMockEnvironment = ExternalMockEnvironment()
    private val valkeyStore = mockk<ValkeyStore>(relaxed = true)
    private val mockHttpClient = getMockHttpClient(env = externalMockEnvironment.environment)
    private val ONE_HOUR_IN_SECONDS = 1 * 60 * 60L

    private val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        valkeyStore = valkeyStore,
        httpClient = mockHttpClient,
    )

    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT,
    )

    @AfterEach
    fun afterEach() {
        clearMocks(valkeyStore)
    }

    @Nested
    @DisplayName("Get obo-token")
    inner class GetOboTokenTests {

        @Test
        fun `Returns obo-token from AzureAD and stores in cache`() {
            val axsysClientId = externalMockEnvironment.environment.clients.axsys.clientId
            val cacheKey =
                "${AzureAdClient.CACHE_AZUREAD_TOKEN_OBO_KEY_PREFIX}$axsysClientId-${UserConstants.VEILEDER_IDENT}"
            every { valkeyStore.getObject<AzureAdToken?>(key = cacheKey) } returns null

            runBlocking {
                azureAdClient.getOnBehalfOfToken(
                    scopeClientId = axsysClientId,
                    token = Token(validToken),
                    callId = "",
                )
            }

            verify(exactly = 1) { valkeyStore.getObject<AzureAdToken?>(key = cacheKey) }
            verify(exactly = 1) {
                valkeyStore.setObject<Any>(
                    key = cacheKey,
                    value = any(),
                    expireSeconds = ONE_HOUR_IN_SECONDS
                )
            }
        }

        @Test
        fun `Returns obo-token from cache`() {
            val axsysClientId = externalMockEnvironment.environment.clients.axsys.clientId
            val cacheKey =
                "${AzureAdClient.CACHE_AZUREAD_TOKEN_OBO_KEY_PREFIX}$axsysClientId-${UserConstants.VEILEDER_IDENT}"
            every {
                valkeyStore.getObject<AzureAdToken?>(key = cacheKey)
            } returns AzureAdToken(
                accessToken = "123",
                expires = LocalDateTime.now().plusHours(1),
            )

            runBlocking {
                azureAdClient.getOnBehalfOfToken(
                    scopeClientId = axsysClientId,
                    token = Token(validToken),
                    callId = "",
                )
            }

            verify(exactly = 1) { valkeyStore.getObject<AzureAdToken?>(key = cacheKey) }
            verify(exactly = 0) { valkeyStore.setObject<Any>(any(), any(), any()) }
        }

        @Test
        fun `Does not store cache when azureAd return null`() {
            val validTokenReturningNull = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT_NO_AZURE_AD_TOKEN,
            )
            val axsysClientId = externalMockEnvironment.environment.clients.axsys.clientId
            val cacheKey =
                "${AzureAdClient.CACHE_AZUREAD_TOKEN_OBO_KEY_PREFIX}$axsysClientId-${UserConstants.VEILEDER_IDENT_NO_AZURE_AD_TOKEN}"
            every { valkeyStore.getObject<AzureAdToken?>(key = cacheKey) } returns null

            runBlocking {
                azureAdClient.getOnBehalfOfToken(
                    scopeClientId = axsysClientId,
                    token = Token(validTokenReturningNull),
                    callId = "",
                )
            }

            verify(exactly = 1) { valkeyStore.getObject<AzureAdToken?>(key = cacheKey) }
            verify(exactly = 0) { valkeyStore.setObject<Any>(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("Get graphApi obo token")
    inner class GetGraphApiOboTokenTests {

        @Test
        fun `Returns graphApi obo-token from AzureAD and stores in cache`() {
            val graphApiClientId = externalMockEnvironment.environment.clients.graphApiUrl
            val cacheKey =
                "${AzureAdClient.CACHE_AZUREAD_TOKEN_OBO_KEY_PREFIX}$graphApiClientId-${UserConstants.VEILEDER_IDENT}"
            every { valkeyStore.getObject<AzureAdToken?>(key = cacheKey) } returns null

            runBlocking {
                azureAdClient.getOnBehalfOfTokenForGraphApi(
                    scopeClientId = graphApiClientId,
                    token = Token(validToken),
                    callId = "",
                )
            }

            verify(exactly = 1) { valkeyStore.getObject<AzureAdToken?>(key = cacheKey) }
            verify(exactly = 1) {
                valkeyStore.setObject<Any>(
                    key = cacheKey,
                    value = any(),
                    expireSeconds = ONE_HOUR_IN_SECONDS
                )
            }
        }

        @Test
        fun `Returns graphApi obo-token from cache`() {
            val graphApiClientId = externalMockEnvironment.environment.clients.graphApiUrl
            val cacheKey =
                "${AzureAdClient.CACHE_AZUREAD_TOKEN_OBO_KEY_PREFIX}$graphApiClientId-${UserConstants.VEILEDER_IDENT}"
            every {
                valkeyStore.getObject<AzureAdToken?>(key = cacheKey)
            } returns AzureAdToken(
                accessToken = "123",
                expires = LocalDateTime.now().plusHours(1),
            )

            runBlocking {
                azureAdClient.getOnBehalfOfToken(
                    scopeClientId = graphApiClientId,
                    token = Token(validToken),
                    callId = "",
                )
            }

            verify(exactly = 1) { valkeyStore.getObject<AzureAdToken?>(key = cacheKey) }
            verify(exactly = 0) { valkeyStore.setObject<Any>(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("Get system token")
    inner class GetSystemToken {

        @Test
        fun `Returns system-token from AzureAD and stores in cache`() {
            val pdlClientId = externalMockEnvironment.environment.clients.pdl.clientId
            val cacheKey = "${AzureAdClient.CACHE_AZUREAD_TOKEN_SYSTEM_KEY_PREFIX}$pdlClientId"
            every { valkeyStore.getObject<AzureAdToken?>(any()) } returns null

            runBlocking {
                azureAdClient.getSystemToken(
                    scopeClientId = pdlClientId,
                    callId = "",
                )
            }

            verify(exactly = 1) { valkeyStore.getObject<AzureAdToken?>(key = cacheKey) }
            verify(exactly = 1) {
                valkeyStore.setObject<Any>(
                    key = cacheKey,
                    value = any(),
                    expireSeconds = ONE_HOUR_IN_SECONDS
                )
            }
        }

        @Test
        fun `Returns system-token from cache`() {
            val pdlClientId = externalMockEnvironment.environment.clients.pdl.clientId
            val cacheKey = "${AzureAdClient.CACHE_AZUREAD_TOKEN_SYSTEM_KEY_PREFIX}$pdlClientId"
            every {
                valkeyStore.getObject<AzureAdToken?>(key = cacheKey)
            } returns AzureAdToken(
                accessToken = "123",
                expires = LocalDateTime.now().plusHours(1),
            )

            runBlocking {
                azureAdClient.getSystemToken(
                    scopeClientId = pdlClientId,
                    callId = "",
                )
            }

            verify(exactly = 1) { valkeyStore.getObject<AzureAdToken?>(key = cacheKey) }
            verify(exactly = 0) { valkeyStore.setObject<Any>(any(), any(), any()) }
        }
    }
}
