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
import no.nav.syfo.util.configuredJacksonMapper
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
        valkeyStore = valkeyStore,
        adRoller = adRoller,
    )

    val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT,
    )
    val validTokenNoAccess = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT_NO_SYFO_ACCESS,
    )

    fun createGroup(groupId: String = "UUID", displayName: String): Group =
        Group().apply {
            this.id = groupId
            this.displayName = displayName
        }

    @BeforeEach
    fun beforeEach() {
        clearMocks(valkeyStore)
        every { valkeyStore.objectMapper } returns configuredJacksonMapper()
    }

    @Test
    fun `Returns syfo role and one enhet - Stores in cache`() {
        val syfoGroup = createGroup(groupId = "syfoId", displayName = "0000-GA-SYFO-SENSITIV")
        val enhetGroup = createGroup(groupId = "enhetId", displayName = "0000-GA-ENHET_1234")
        val graphApiClientMock = spyk(graphApiClient)
        coEvery { graphApiClientMock.getGroupsForVeilederRequest(any(), any()) } returns listOf(syfoGroup, enhetGroup)

        val cacheKey = cacheKeyVeilederGrupper(UserConstants.VEILEDER_IDENT)
        every { valkeyStore.get(cacheKey) } returns null // Ettersom getListObject er inline er det egentlig dette som må mockes

        val grupper = runBlocking {
            graphApiClientMock.getGrupperForVeilederOgCache(
                token = Token(validToken),
                callId = UUID.randomUUID().toString(),
            )
        }

        assertEquals(2, grupper.size)
        assertEquals(syfoGroup.displayName, grupper.first().adGruppenavn)
        assertEquals(enhetGroup.displayName, grupper.last().adGruppenavn)
        verify(exactly = 1) { valkeyStore.get(cacheKey) }
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
        val syfoGroup = createGroup(groupId = "syfoId", displayName = "0000-GA-SYFO-SENSITIV")
        val enhetGroup = createGroup(groupId = "enhetId", displayName = "0000-GA-ENHET_1234")
        val graphApiClientMock = spyk(graphApiClient)
        coEvery { graphApiClientMock.getGroupsForVeilederRequest(any(), any()) } returns listOf(syfoGroup, enhetGroup)

        val cacheKey = cacheKeyVeilederGrupper(UserConstants.VEILEDER_IDENT)
        every { valkeyStore.get(cacheKey) } returns """[{"uuid":"syfoId","adGruppenavn":"0000-GA-SYFO-SENSITIV"},{"uuid":"enhetId","adGruppenavn":"0000-GA-ENHET_1234"}]"""

        val grupper = runBlocking {
            graphApiClientMock.getGrupperForVeilederOgCache(
                token = Token(validToken),
                callId = UUID.randomUUID().toString(),
            )
        }

        assertEquals(2, grupper.size)
        assertEquals(syfoGroup.displayName, grupper.first().adGruppenavn)
        assertEquals(enhetGroup.displayName, grupper.last().adGruppenavn)
        verify(exactly = 1) { valkeyStore.get(cacheKey) }
        verify(exactly = 0) {
            valkeyStore.setObject<List<Gruppe>>(
                key = eq(cacheKey),
                value = any(),
                expireSeconds = eq(GraphApiClient.TWELVE_HOURS_IN_SECS),
            )
        }
        coVerify(exactly = 0) { graphApiClientMock.getGroupsForVeilederRequest(any(), any()) }
    }

    @Test
    fun `Returns only syfo access and does not store in cache`() {
        val syfoGroup = createGroup(groupId = "syfoId", displayName = "0000-GA-SYFO-SENSITIV")
        val graphApiClientMock = spyk(graphApiClient)
        coEvery { graphApiClientMock.getGroupsForVeilederRequest(any(), any()) } returns listOf(syfoGroup)

        val cacheKey = cacheKeyVeilederGrupper(UserConstants.VEILEDER_IDENT)
        every { valkeyStore.get(any()) } returns null

        val grupper = runBlocking {
            graphApiClientMock.getGrupperForVeilederOgCache(
                token = Token(validToken),
                callId = UUID.randomUUID().toString(),
            )
        }

        assertEquals(1, grupper.size)
        assertEquals(syfoGroup.displayName, grupper.first().adGruppenavn)
        verify(exactly = 1) { valkeyStore.get(cacheKey) }
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
        val graphApiClientMock = spyk(graphApiClient)
        coEvery { graphApiClientMock.getGroupsForVeilederRequest(any(), any()) } returns listOf()

        val cacheKey = cacheKeyVeilederGrupper(UserConstants.VEILEDER_IDENT_NO_SYFO_ACCESS)
        every { valkeyStore.get(any()) } returns null

        val grupper = runBlocking {
            graphApiClientMock.getGrupperForVeilederOgCache(
                token = Token(validTokenNoAccess),
                callId = UUID.randomUUID().toString(),
            )
        }

        assertEquals(0, grupper.size)
        verify(exactly = 1) { valkeyStore.get(cacheKey) }
        verify(exactly = 0) {
            valkeyStore.setObject<List<Gruppe>>(
                key = eq(cacheKey),
                value = any(),
                expireSeconds = eq(GraphApiClient.TWELVE_HOURS_IN_SECS),
            )
        }
    }

    @Test
    fun `Kall pa grupper for veileder feiler med ODataError (ApiException) skal returnere tom liste`() {
        val graphApiClientStub = spyk(graphApiClient)
        coEvery {
            graphApiClientStub.getGroupsForVeilederRequest(any(), any())
        } throws ODataError().apply {
            error = MainError().apply { this.code = "400" }
                .apply { this.message = "Error when calling Microsoft Graph API" }
        }
        every { valkeyStore.get(any()) } returns null

        testApplication {
            val grupper = graphApiClientStub.getGrupperForVeilederOgCache(
                token = Token(validToken),
                callId = "callId"
            )

            assertTrue(grupper.isEmpty())
        }
    }

    @Test
    fun `Kall pa grupper for veileder feiler med IllegalAccessException (Exception) skal returnere tom liste`() {
        val graphApiClientStub = spyk(graphApiClient)
        coEvery {
            graphApiClientStub.getGroupsForVeilederRequest(any(), any())
        } throws IllegalAccessException("Some access error")
        every { valkeyStore.get(any()) } returns null

        testApplication {
            val grupper = graphApiClientStub.getGrupperForVeilederOgCache(
                token = Token(validToken),
                callId = "callId"
            )

            assertTrue(grupper.isEmpty())
        }
    }
}
