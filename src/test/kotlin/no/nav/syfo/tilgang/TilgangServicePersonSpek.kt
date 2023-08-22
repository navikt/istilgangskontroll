package no.nav.syfo.tilgang

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.axsys.AxsysClient
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.skjermedepersoner.SkjermedePersonerPipClient
import no.nav.syfo.domain.Personident
import no.nav.syfo.testhelper.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class TilgangServicePersonSpek : Spek({
    val graphApiClient = mockk<GraphApiClient>(relaxed = true)
    val axsysClient = mockk<AxsysClient>(relaxed = true)
    val skjermedePersonerPipClient = mockk<SkjermedePersonerPipClient>(relaxed = true)
    val redisStore = mockk<RedisStore>(relaxed = true)
    val externalMockEnvironment = ExternalMockEnvironment()
    val adRoller = AdRoller(externalMockEnvironment.environment)

    val tilgangService = TilgangService(
        graphApiClient = graphApiClient,
        adRoller = adRoller,
        redisStore = redisStore,
        axsysClient = axsysClient,
        skjermedePersonerPipClient = skjermedePersonerPipClient,
    )

    val TWELVE_HOURS_IN_SECONDS = 12 * 60 * 60L

    fun verifyCacheSet(exactly: Int, key: String = "", harTilgang: Boolean = true) {
        verify(exactly = exactly) {
            if (exactly == 0) {
                redisStore.setObject<Any>(any(), any(), any())
            } else {
                redisStore.setObject(
                    key = key,
                    value = Tilgang(erGodkjent = harTilgang),
                    expireSeconds = TWELVE_HOURS_IN_SECONDS
                )
            }
        }
    }

    describe("TilgangService tilgangToPerson") {
        val validToken = Token(
            generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
            )
        )

        afterEachTest {
            clearMocks(graphApiClient, redisStore, skjermedePersonerPipClient)
        }

        describe("has access to person") {
            it("Return no access if person is skjermet and veileder doesn't have correct AdRolle") {
                val personident = Personident(UserConstants.PERSONIDENT)
                val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
                val callId = "123"
                every { redisStore.getObject<Tilgang?>(any()) } returns null
                coEvery { skjermedePersonerPipClient.isSkjermet(any(), personident, any()) } returns true
                coEvery { graphApiClient.hasAccess(any(), any(), any()) } returns false

                runBlocking {
                    val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

                    tilgang.erGodkjent shouldBeEqualTo false
                }

                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                coVerify(exactly = 1) { skjermedePersonerPipClient.isSkjermet(callId = callId, personIdent = personident, token = validToken) }
                coVerify(exactly = 1) { graphApiClient.hasAccess(adRolle = adRoller.EGEN_ANSATT, token = validToken, callId = callId) }
                verifyCacheSet(exactly = 1, key = cacheKey, harTilgang = false)
            }

            it("return godkjent access if person is skjermet and veileder has correct AdRolle") {
                val personident = Personident(UserConstants.PERSONIDENT)
                val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
                val callId = "123"
                every { redisStore.getObject<Tilgang?>(any()) } returns null
                coEvery { skjermedePersonerPipClient.isSkjermet(any(), personident, any()) } returns true
                coEvery { graphApiClient.hasAccess(any(), any(), any()) } returns true

                runBlocking {
                    val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

                    tilgang.erGodkjent shouldBeEqualTo true
                }

                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                coVerify(exactly = 1) { skjermedePersonerPipClient.isSkjermet(callId = callId, personIdent = personident, token = validToken) }
                coVerify(exactly = 1) { graphApiClient.hasAccess(adRolle = adRoller.EGEN_ANSATT, token = validToken, callId = callId) }
                verifyCacheSet(exactly = 1, key = cacheKey)
            }

            it("return result from cache hit") {
                val personident = Personident(UserConstants.PERSONIDENT)
                val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
                val callId = "123"
                every { redisStore.getObject<Tilgang?>(any()) } returns Tilgang(erGodkjent = true)

                runBlocking {
                    val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

                    tilgang.erGodkjent shouldBeEqualTo true
                }

                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                coVerify(exactly = 0) { skjermedePersonerPipClient.isSkjermet(any(), personident, any()) }
                coVerify(exactly = 0) { graphApiClient.hasAccess(any(), any(), any()) }
                verifyCacheSet(exactly = 0)
            }
        }
    }
})
