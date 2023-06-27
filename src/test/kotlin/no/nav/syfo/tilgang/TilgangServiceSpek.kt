package no.nav.syfo.tilgang

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.testhelper.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class TilgangServiceSpek : Spek({
    val graphApiClient = mockk<GraphApiClient>(relaxed = true)
    val redisStore = mockk<RedisStore>(relaxed = true)
    val externalMockEnvironment = ExternalMockEnvironment()
    val adRoller = AdRoller(externalMockEnvironment.environment)

    val tilgangService = TilgangService(
        graphApiClient = graphApiClient,
        adRoller = adRoller,
        redisStore = redisStore,
    )

    val TWELVE_HOURS_IN_SECONDS = 12 * 60 * 60L

    describe("sjekkTilgangTilTjenesten") {
        val cacheKey = "tilgang-til-tjenesten-${UserConstants.VEILEDER_IDENT}"
        val validToken = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT,
        )

        afterEachTest {
            clearMocks(graphApiClient, redisStore)
        }

        it("cache response from GraphApiClient on cache miss") {
            val callId = "123"
            every { redisStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.hasAccess(any(), any(), any()) } returns true

            runBlocking {
                tilgangService.sjekkTilgangTilTjenesten(validToken, callId)
            }

            verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 1) { graphApiClient.hasAccess(adRoller.SYFO, validToken, callId) }
            verify(exactly = 1) {
                redisStore.setObject(
                    key = cacheKey,
                    value = Tilgang(harTilgang = true),
                    expireSeconds = TWELVE_HOURS_IN_SECONDS
                )
            }
        }

        it("return result from cache hit") {
            val callId = "123"
            every { redisStore.getObject<Tilgang?>(any()) } returns Tilgang(harTilgang = true)

            runBlocking {
                tilgangService.sjekkTilgangTilTjenesten(validToken, callId)
            }

            verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 0) { graphApiClient.hasAccess(any(), any(), any()) }
            verify(exactly = 0) { redisStore.setObject<Any>(any(), any(), any()) }
        }
    }
})
