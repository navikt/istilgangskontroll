package no.nav.syfo.client.graphapi

import com.microsoft.graph.models.Group
import com.microsoft.graph.models.odataerrors.MainError
import com.microsoft.graph.models.odataerrors.ODataError
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.cache.getListObject
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.graphapi.GraphApiClient.Companion.cacheKeyVeilederGrupper
import no.nav.syfo.mocks.getMockHttpClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generateJWT
import no.nav.syfo.tilgang.AdRoller
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class GraphApiClientTest {
    private val externalMockEnvironment = ExternalMockEnvironment()
    private val valkeyStore = externalMockEnvironment.valkeyStore
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

    @Test
    fun `Returns syfo role and one enhet - Stores in cache`() {
        val syfoGroup = createGroup(groupId = adRoller.SYFO_FULL.id, displayName = "0000-CA-MODIA-SYFO-VEILEDER")
        val enhetGroup = createGroup(groupId = "enhetId", displayName = "0000-GA-ENHET_1234")
        val graphApiClientMock = spyk(graphApiClient)
        coEvery { graphApiClientMock.getGroupsForVeilederRequest(any(), any()) } returns listOf(syfoGroup, enhetGroup)

        val cacheKey = cacheKeyVeilederGrupper(UserConstants.VEILEDER_IDENT)

        val grupper = runBlocking {
            graphApiClientMock.getGrupperForVeilederOgCache(
                token = Token(validToken),
                callId = UUID.randomUUID().toString(),
            )
        }

        assertEquals(2, grupper.size)
        assertEquals(syfoGroup.displayName, grupper.first().adGruppenavn)
        assertEquals(enhetGroup.displayName, grupper.last().adGruppenavn)
        coVerify(exactly = 1) { graphApiClientMock.getGroupsForVeilederRequest(any(), any()) }

        val cachedGrupper = valkeyStore.getListObject<Gruppe>(cacheKey)
        assertEquals(2, cachedGrupper?.size)
    }

    @Test
    fun `Returns cached syfo access and one enhet - Grupper should not be cached more than once`() {
        val syfoGroup = createGroup(groupId = "syfoId", displayName = "0000-GA-SYFO-SENSITIV")
        val enhetGroup = createGroup(groupId = "enhetId", displayName = "0000-GA-ENHET_1234")
        val graphApiClientMock = spyk(graphApiClient)
        coEvery { graphApiClientMock.getGroupsForVeilederRequest(any(), any()) } returns listOf(syfoGroup, enhetGroup)

        val cacheKey = cacheKeyVeilederGrupper(UserConstants.VEILEDER_IDENT)
        valkeyStore.setObject(
            key = cacheKey,
            value = listOf(
                Gruppe(uuid = syfoGroup.id, adGruppenavn = syfoGroup.displayName),
                Gruppe(uuid = enhetGroup.id, adGruppenavn = enhetGroup.displayName),
            ),
            expireSeconds = GraphApiClient.TWELVE_HOURS_IN_SECS,
        )

        val grupper = runBlocking {
            graphApiClientMock.getGrupperForVeilederOgCache(
                token = Token(validToken),
                callId = UUID.randomUUID().toString(),
            )
        }

        assertEquals(2, grupper.size)
        assertEquals(syfoGroup.displayName, grupper.first().adGruppenavn)
        assertEquals(enhetGroup.displayName, grupper.last().adGruppenavn)
        coVerify(exactly = 0) { graphApiClientMock.getGroupsForVeilederRequest(any(), any()) }
    }

    @Test
    fun `Returns only syfo access and does not store in cache`() {
        val syfoGroup = createGroup(groupId = "syfoId", displayName = "0000-GA-SYFO-SENSITIV")
        val graphApiClientMock = spyk(graphApiClient)
        coEvery { graphApiClientMock.getGroupsForVeilederRequest(any(), any()) } returns listOf(syfoGroup)

        val cacheKey = cacheKeyVeilederGrupper(UserConstants.VEILEDER_IDENT)

        val grupper = runBlocking {
            graphApiClientMock.getGrupperForVeilederOgCache(
                token = Token(validToken),
                callId = UUID.randomUUID().toString(),
            )
        }

        assertEquals(1, grupper.size)
        assertEquals(syfoGroup.displayName, grupper.first().adGruppenavn)
        assertNull(valkeyStore.get(cacheKey))
    }

    @Test
    fun `Denies syfo access and does not store in cache`() {
        val graphApiClientMock = spyk(graphApiClient)
        coEvery { graphApiClientMock.getGroupsForVeilederRequest(any(), any()) } returns listOf()

        val cacheKey = cacheKeyVeilederGrupper(UserConstants.VEILEDER_IDENT_NO_SYFO_ACCESS)

        val grupper = runBlocking {
            graphApiClientMock.getGrupperForVeilederOgCache(
                token = Token(validTokenNoAccess),
                callId = UUID.randomUUID().toString(),
            )
        }

        assertEquals(0, grupper.size)
        assertNull(valkeyStore.get(cacheKey))
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

        testApplication {
            val grupper = graphApiClientStub.getGrupperForVeilederOgCache(
                token = Token(validToken),
                callId = "callId"
            )

            assertTrue(grupper.isEmpty())
        }
    }
}
