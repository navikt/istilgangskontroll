package no.nav.syfo.client.graphapi

import com.microsoft.graph.models.Group
import com.microsoft.graph.models.odataerrors.MainError
import com.microsoft.graph.models.odataerrors.ODataError
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.cache.ValkeyStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.graphapi.GraphApiClient.Companion.cacheKeyVeilederGrupper
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
        proxyHttpClient = mockHttpClient,
    )

    private val graphApiClient = GraphApiClient(
        azureAdClient = azureAdClient,
        baseUrl = externalMockEnvironment.environment.clients.graphApiUrl,
        valkeyStore = valkeyStore,
        adRoller = adRoller,
    )

    @BeforeEach
    fun beforeEach() {
        clearMocks(valkeyStore)
    }

    @Test
    fun `Returns syfo access and one enhet - Stores in cache`() {
        val validToken = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT,
        )
        val syfoGruppe = Gruppe(uuid = "syfoId", adGruppenavn = "0000-GA-SYFO-SENSITIV")
        val enhetGroup = Gruppe(uuid = "UUID", adGruppenavn = "0000-GA-ENHET_1234")
        val graphApiClientMock = spyk(graphApiClient)
        coEvery { graphApiClientMock.getGrupperForVeileder(any(), any()) } returns listOf(syfoGruppe, enhetGroup)

        val cacheKey = cacheKeyVeilederGrupper(UserConstants.VEILEDER_IDENT)
        every {
            valkeyStore.getListObject<Gruppe>(cacheKey)
        } returns null
        every {
            valkeyStore.get(any<String>())
        } returns null

        val hasAccess = runBlocking {
            graphApiClientMock.hasAccess(
                adRolle = adRoller.SYFO,
                token = Token(validToken),
                callId = UUID.randomUUID().toString(),
            )
        }
        assertTrue(hasAccess)
        verify(exactly = 1) { valkeyStore.get(key = eq(cacheKey)) }
        verify(exactly = 1) {
            valkeyStore.setObject<List<Gruppe>>(
                key = eq(cacheKey),
                value = any(),
                expireSeconds = eq(GraphApiClient.TWELVE_HOURS_IN_SECS),
            )
        }
    }

    @Test
    fun `Returns cached syfo access and one enhet - Grupper should not be cached more than once`() {
        val validToken = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT,
        )
        val syfoGruppe = Gruppe(uuid = "syfoId", adGruppenavn = "0000-GA-SYFO-SENSITIV")
        val enhetGroup = Gruppe(uuid = "UUID", adGruppenavn = "0000-GA-ENHET_1234")
        val graphApiClientMock = spyk(graphApiClient)

        val cacheKey = cacheKeyVeilederGrupper(UserConstants.VEILEDER_IDENT)
        every {
            valkeyStore.getListObject<Gruppe>(cacheKey)
        } returns listOf(syfoGruppe, enhetGroup)

        val hasAccess = runBlocking {
            graphApiClientMock.hasAccess(
                adRolle = adRoller.SYFO,
                token = Token(validToken),
                callId = UUID.randomUUID().toString(),
            )
        }
        assertTrue(hasAccess)
        verify(exactly = 1) { valkeyStore.get(key = eq(cacheKey)) }
        verify(exactly = 0) {
            valkeyStore.setObject<List<Gruppe>>(
                key = eq(cacheKey),
                value = any(),
                expireSeconds = eq(GraphApiClient.TWELVE_HOURS_IN_SECS),
            )
        }
    }

    @Test
    fun `Returns only syfo access and does not store in cache`() {
        val validToken = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT,
        )
        val syfoGruppe = Gruppe(uuid = "syfoId", adGruppenavn = "0000-GA-SYFO-SENSITIV")
        val graphApiClientMock = spyk(graphApiClient)
        coEvery { graphApiClientMock.getGrupperForVeileder(any(), any()) } returns listOf(syfoGruppe)

        val cacheKey = cacheKeyVeilederGrupper(UserConstants.VEILEDER_IDENT)
        every {
            valkeyStore.getListObject<Gruppe>(cacheKey)
        } returns null
        every {
            valkeyStore.get(any<String>())
        } returns null

        val hasAccess = runBlocking {
            graphApiClientMock.hasAccess(
                adRolle = adRoller.SYFO,
                token = Token(validToken),
                callId = UUID.randomUUID().toString(),
            )
        }
        assertTrue(hasAccess)
        verify(exactly = 1) { valkeyStore.get(key = eq(cacheKey)) }
        verify(exactly = 0) {
            valkeyStore.setObject<List<Gruppe>>(
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
        val graphApiClientMock = spyk(graphApiClient)
        coEvery { graphApiClientMock.getGrupperForVeileder(any(), any()) } returns listOf()

        val cacheKey = cacheKeyVeilederGrupper(UserConstants.VEILEDER_IDENT_NO_SYFO_ACCESS)
        every {
            valkeyStore.getListObject<Gruppe>(cacheKey)
        } returns null
        every {
            valkeyStore.get(any<String>())
        } returns null

        val hasAccess = runBlocking {
            graphApiClientMock.hasAccess(
                adRolle = adRoller.SYFO,
                token = Token(validToken),
                callId = UUID.randomUUID().toString(),
            )
        }
        assertFalse(hasAccess)
        verify(exactly = 1) { valkeyStore.get(key = eq(cacheKey)) }
        verify(exactly = 0) {
            valkeyStore.setObject<List<Gruppe>>(
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
            val grupper = graphApiClientStub.getGrupperForVeileder(
                token = Token("eyJhbGciOiJIUz..."),
                callId = "callId"
            )

            assertEquals(2, grupper.size)

            val enhetGruppe = grupper.first()
            assertEquals("UUID", enhetGruppe.uuid)
            assertEquals("0000-GA-ENHET_1234", enhetGruppe.adGruppenavn)

            val syfoGruppe = grupper.last()
            assertEquals("UUID2", syfoGruppe.uuid)
            assertEquals("0000-GA-SYFO-SENSITIV", syfoGruppe.adGruppenavn)
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
            val grupper = graphApiClientStub.getGrupperForVeileder(
                token = Token("eyJhbGciOiJIUz..."),
                callId = "callId"
            )

            assertTrue(grupper.isEmpty())
        }
    }

    @Test
    fun `Kall på grupper for veileder feiler med IllegalAccessException (Exception) skal returnere tom liste`() {
        val graphApiClientStub = spyk(graphApiClient)
        coEvery {
            graphApiClientStub.getGroupsForVeilederRequest(any(), any())
        } throws IllegalAccessException("Some access error")

        testApplication {
            val grupper = graphApiClientStub.getGrupperForVeileder(
                token = Token("eyJhbGciOiJIUz..."),
                callId = "callId"
            )

            assertTrue(grupper.isEmpty())
        }
    }

    @Test
    fun `get enheter for veileder - Veileder har flere grupper, men kun en enhet`() {
        val graphApiClientStub = spyk(graphApiClient)
        val enhetGroup = Gruppe(uuid = "UUID", adGruppenavn = "0000-GA-ENHET_1234")
        val syfoGroup = Gruppe(uuid = "UUID2", adGruppenavn = "0000-GA-SYFO-SENSITIV")
        coEvery {
            graphApiClientStub.getGrupperForVeilederOgCache(any(), any())
        } returns listOf(enhetGroup, syfoGroup)

        testApplication {
            val enheter = graphApiClientStub.getEnheterForVeileder(
                token = Token("eyJhbGciOiJIUz..."),
                callId = "callId"
            )

            assertEquals(1, enheter.size)
            assertEquals("1234", enheter.first().id)
        }
    }
}
