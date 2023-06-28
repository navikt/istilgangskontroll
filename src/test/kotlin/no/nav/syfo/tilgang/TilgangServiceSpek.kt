package no.nav.syfo.tilgang

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.axsys.AxsysClient
import no.nav.syfo.client.axsys.AxsysEnhet
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.testhelper.*
import org.amshove.kluent.`should be equal to`
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class TilgangServiceSpek : Spek({
    val graphApiClient = mockk<GraphApiClient>(relaxed = true)
    val axsysClient = mockk<AxsysClient>(relaxed = true)
    val redisStore = mockk<RedisStore>(relaxed = true)
    val externalMockEnvironment = ExternalMockEnvironment()
    val adRoller = AdRoller(externalMockEnvironment.environment)

    val tilgangService = TilgangService(
        graphApiClient = graphApiClient,
        adRoller = adRoller,
        redisStore = redisStore,
        axsysClient = axsysClient,
    )

    val TWELVE_HOURS_IN_SECONDS = 12 * 60 * 60L

    describe("sjekkTilgangTilTjenesten") {
        val validToken = Token(
            generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
            )
        )

        afterEachTest {
            clearMocks(graphApiClient, redisStore, axsysClient)
        }

        describe("has access to SYFO") {
            val cacheKey = "tilgang-til-tjenesten-${UserConstants.VEILEDER_IDENT}"

            it("cache response from GraphApiClient on cache miss") {
                val callId = "123"
                every { redisStore.getObject<Tilgang?>(any()) } returns null
                coEvery { graphApiClient.hasAccess(any(), any(), any()) } returns true

                runBlocking {
                    tilgangService.hasTilgangTilSyfo(validToken, callId)
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
                    tilgangService.hasTilgangTilSyfo(validToken, callId)
                }

                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                coVerify(exactly = 0) { graphApiClient.hasAccess(any(), any(), any()) }
                verify(exactly = 0) { redisStore.setObject<Any>(any(), any(), any()) }
            }
        }

        describe("has access to enhet") {
            it("return has access if enhet is in list from Axsys") {
                val enhet = Enhet(UserConstants.VEILEDER_ENHET)
                val veiledersEnhet = AxsysEnhet(
                    enhetId = enhet.id,
                    navn = "enhet",
                )
                val cacheKey = "tilgang-til-enhet-${UserConstants.VEILEDER_IDENT}-$enhet"
                val callId = "123"
                every { redisStore.getObject<Tilgang?>(any()) } returns null
                coEvery { axsysClient.getEnheter(validToken, callId) } returns listOf(veiledersEnhet)

                runBlocking {
                    val tilgang = tilgangService.hasTilgangToEnhet(validToken, callId, enhet)

                    tilgang.harTilgang `should be equal to` true
                }

                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                coVerify(exactly = 1) { axsysClient.getEnheter(validToken, callId) }
                verify(exactly = 1) {
                    redisStore.setObject(
                        key = cacheKey,
                        value = Tilgang(harTilgang = true),
                        expireSeconds = TWELVE_HOURS_IN_SECONDS
                    )
                }
            }

            it("return no access if enhet is not in list from Axsys") {
                val wantedEnhet = Enhet(UserConstants.VEILEDER_ENHET)
                val actualEnhet = Enhet(UserConstants.VEILEDER_ENHET_NO_ACCESS)
                val veiledersEnhet = AxsysEnhet(
                    enhetId = actualEnhet.id,
                    navn = "enhet",
                )
                val cacheKey = "tilgang-til-enhet-${UserConstants.VEILEDER_IDENT}-$wantedEnhet"
                val callId = "123"
                every { redisStore.getObject<Tilgang?>(any()) } returns null
                coEvery { axsysClient.getEnheter(validToken, callId) } returns listOf(veiledersEnhet)

                runBlocking {
                    val tilgang = tilgangService.hasTilgangToEnhet(validToken, callId, wantedEnhet)

                    tilgang.harTilgang `should be equal to` false
                }

                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                coVerify(exactly = 1) { axsysClient.getEnheter(validToken, callId) }
                verify(exactly = 1) {
                    redisStore.setObject(
                        key = cacheKey,
                        value = Tilgang(harTilgang = false),
                        expireSeconds = TWELVE_HOURS_IN_SECONDS
                    )
                }
            }

            it("return result from cache hit") {
                val enhet = Enhet(UserConstants.VEILEDER_ENHET)
                val cacheKey = "tilgang-til-enhet-${UserConstants.VEILEDER_IDENT}-$enhet"
                val callId = "123"
                every { redisStore.getObject<Tilgang?>(cacheKey) } returns Tilgang(harTilgang = true)

                runBlocking {
                    val tilgang = tilgangService.hasTilgangToEnhet(validToken, callId, enhet)

                    tilgang.harTilgang `should be equal to` true
                }

                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                coVerify(exactly = 0) { axsysClient.getEnheter(any(), any()) }
                verify(exactly = 0) { redisStore.setObject<Any>(any(), any(), any()) }
            }
        }
    }
})
