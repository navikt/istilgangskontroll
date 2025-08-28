package no.nav.syfo.client.graphapi

import com.microsoft.graph.models.Group
import com.microsoft.graph.models.odataerrors.MainError
import com.microsoft.graph.models.odataerrors.ODataError
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.mocks.getMockHttpClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generateJWT
import no.nav.syfo.tilgang.AdRoller
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

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

    fun group(groupId: String = "UUID", displayName: String): Group {
        val group = Group()
        group.id = groupId
        group.displayName = displayName
        return group
    }

    @Test
    fun `Veileder har grupper`() {
        val graphApiClientStub = spyk(graphApiClient)
        val enhetGroup = group(groupId = "UUID", displayName = "0000-GA-ENHET_1234")
        val syfoGroup = group(groupId = "UUID2", displayName = "0000-GA-SYFO-SENSITIV")
        coEvery {
            graphApiClientStub.getGroupsForVeilederRequest(any(), any())
        } returns listOf(enhetGroup, syfoGroup)

        testApplication {
            val graphApiGroups = graphApiClientStub.getGroupsForVeileder(
                token = Token("eyJhbGciOiJIUz..."),
                callId = "callId"
            )

            assertEquals(2, graphApiGroups.size)

            val enhetGroup = graphApiGroups.first()
            assertEquals("UUID", enhetGroup.id)
            assertEquals("0000-GA-ENHET_1234", enhetGroup.displayName)

            val syfoGroup = graphApiGroups.last()
            assertEquals("UUID2", syfoGroup.id)
            assertEquals("0000-GA-SYFO-SENSITIV", syfoGroup.displayName)
        }
    }

    @Test
    fun `Kall på grupper for veileder feiler med ODataError (ApiException) skal returnere tom liste`() {
        val graphApiClientStub = spyk(graphApiClient)
        coEvery {
            graphApiClientStub.getGroupsForVeilederRequest(any(), any())
        } throws ODataError().apply {
            error = MainError().apply { this.code = "400" }
                .apply { this.message = "Error when calling Microsoft Graph API" }
        }

        testApplication {
            val graphApiGroups = graphApiClientStub.getGroupsForVeileder(
                token = Token("eyJhbGciOiJIUz..."),
                callId = "callId"
            )

            assertTrue(graphApiGroups.isEmpty())
        }
    }

    @Test
    fun `Kall på grupper for veileder feiler med IllegalAccessException (Exception) skal returnere tom liste`() {
        val graphApiClientStub = spyk(graphApiClient)
        coEvery {
            graphApiClientStub.getGroupsForVeilederRequest(any(), any())
        } throws IllegalAccessException("Some access error")

        testApplication {
            val graphApiGroups = graphApiClientStub.getGroupsForVeileder(
                token = Token("eyJhbGciOiJIUz..."),
                callId = "callId"
            )

            assertTrue(graphApiGroups.isEmpty())
        }
    }
}
