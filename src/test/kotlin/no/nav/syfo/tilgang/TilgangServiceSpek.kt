package no.nav.syfo.tilgang

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.axsys.AxsysClient
import no.nav.syfo.client.axsys.AxsysEnhet
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.norg.NorgClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.skjermedepersoner.SkjermedePersonerPipClient
import no.nav.syfo.domain.Personident
import no.nav.syfo.testhelper.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class TilgangServiceSpek : Spek({
    val graphApiClient = mockk<GraphApiClient>(relaxed = true)
    val axsysClient = mockk<AxsysClient>(relaxed = true)
    val skjermedePersonerPipClient = mockk<SkjermedePersonerPipClient>(relaxed = true)
    val pdlClient = mockk<PdlClient>(relaxed = true)
    val behandlendeEnhetClient = mockk<BehandlendeEnhetClient>(relaxed = true)
    val norgClient = mockk<NorgClient>(relaxed = true)
    val redisStore = mockk<RedisStore>(relaxed = true)
    val externalMockEnvironment = ExternalMockEnvironment()
    val adRoller = AdRoller(externalMockEnvironment.environment)

    val tilgangService = TilgangService(
        graphApiClient = graphApiClient,
        adRoller = adRoller,
        redisStore = redisStore,
        axsysClient = axsysClient,
        skjermedePersonerPipClient = skjermedePersonerPipClient,
        pdlClient = pdlClient,
        behandlendeEnhetClient = behandlendeEnhetClient,
        norgClient = norgClient,
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

    describe("TilgangService") {
        val validToken = Token(
            generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
            )
        )

        afterEachTest {
            clearMocks(
                graphApiClient,
                axsysClient,
                skjermedePersonerPipClient,
                pdlClient,
                behandlendeEnhetClient,
                redisStore,
            )
        }

        describe("check if veileder has access to SYFO") {
            val cacheKey = "tilgang-til-tjenesten-${UserConstants.VEILEDER_IDENT}"

            it("return result from cache hit") {
                val callId = "123"
                every { redisStore.getObject<Tilgang?>(any()) } returns Tilgang(erGodkjent = true)

                runBlocking {
                    tilgangService.hasTilgangToSyfo(validToken, callId)
                }

                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                coVerify(exactly = 0) { graphApiClient.hasAccess(any(), any(), any()) }
                verifyCacheSet(exactly = 0)
            }

            it("cache response from GraphApiClient on cache miss") {
                val callId = "123"
                every { redisStore.getObject<Tilgang?>(any()) } returns null
                coEvery { graphApiClient.hasAccess(any(), any(), any()) } returns true

                runBlocking {
                    tilgangService.hasTilgangToSyfo(validToken, callId)
                }

                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                coVerify(exactly = 1) { graphApiClient.hasAccess(adRoller.SYFO, validToken, callId) }
                verifyCacheSet(exactly = 1, key = cacheKey)
            }
        }

        describe("check if veileder has access to enhet") {
            val cacheKeySyfo = "tilgang-til-tjenesten-${UserConstants.VEILEDER_IDENT}"

            it("return has access if enhet is in veileders list from Axsys") {
                val enhet = Enhet(UserConstants.ENHET_VEILEDER)
                val veiledersEnhet = AxsysEnhet(
                    enhetId = enhet.id,
                    navn = "enhet",
                )
                val cacheKey = "tilgang-til-enhet-${UserConstants.VEILEDER_IDENT}-$enhet"
                val callId = "123"
                every { redisStore.getObject<Tilgang?>(any()) } returns null
                coEvery { axsysClient.getEnheter(any(), any()) } returns listOf(veiledersEnhet)
                coEvery { graphApiClient.hasAccess(any(), any(), any()) } returns true

                runBlocking {
                    val tilgang = tilgangService.hasTilgangToEnhet(validToken, callId, enhet)

                    tilgang.erGodkjent shouldBeEqualTo true
                }

                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                coVerify(exactly = 1) { axsysClient.getEnheter(validToken, callId) }
                verifyCacheSet(exactly = 1, key = cacheKey)
            }

            it("return no access if enhet is not in veileders list from Axsys") {
                val wantedEnhet = Enhet(UserConstants.ENHET_VEILEDER)
                val actualEnhet = Enhet(UserConstants.ENHET_VEILEDER_NO_ACCESS)
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

                    tilgang.erGodkjent shouldBeEqualTo false
                }

                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                coVerify(exactly = 1) { axsysClient.getEnheter(validToken, callId) }
                verifyCacheSet(exactly = 1, key = cacheKey, harTilgang = false)
            }

            it("return result from cache hit") {
                val enhet = Enhet(UserConstants.ENHET_VEILEDER)
                val cacheKey = "tilgang-til-enhet-${UserConstants.VEILEDER_IDENT}-$enhet"
                val callId = "123"
                every { redisStore.getObject<Tilgang?>(cacheKey) } returns Tilgang(erGodkjent = true)

                runBlocking {
                    val tilgang = tilgangService.hasTilgangToEnhet(validToken, callId, enhet)

                    tilgang.erGodkjent shouldBeEqualTo true
                }

                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                coVerify(exactly = 0) { graphApiClient.hasAccess(any(), any(), any()) }
                coVerify(exactly = 0) { axsysClient.getEnheter(any(), any()) }
                verifyCacheSet(exactly = 0)
            }
        }

        describe("preload cache for person access") {
            it("gets data from behandledeEnhet, skjermedePersonerPip and pdl") {
                val callId = "123"
                val personident = Personident(UserConstants.PERSONIDENT)
                val personidenter = listOf(UserConstants.PERSONIDENT)
                coJustRun { behandlendeEnhetClient.getEnhetWithSystemToken(any(), personident) }
                coJustRun { skjermedePersonerPipClient.getIsSkjermetWithSystemToken(any(), personident) }
                coJustRun { pdlClient.getPersonWithSystemToken(any(), personident) }

                runBlocking {
                    tilgangService.preloadCacheForPersonAccess(
                        callId = callId,
                        personidenter = personidenter,
                    )
                }

                coVerify(exactly = 1) { behandlendeEnhetClient.getEnhetWithSystemToken(callId, personident) }
                coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithSystemToken(callId, personident) }
                coVerify(exactly = 1) { pdlClient.getPersonWithSystemToken(callId, personident) }
            }

            it("gets data from behandledeEnhet, skjermedePersonerPip and pdl for each person in list") {
                val callId = "123"
                val personident1 = Personident(UserConstants.PERSONIDENT)
                val personident2 = Personident(UserConstants.PERSONIDENT_GRADERT)
                val personidenter = listOf(UserConstants.PERSONIDENT, UserConstants.PERSONIDENT_GRADERT)
                coJustRun { behandlendeEnhetClient.getEnhetWithSystemToken(any(), personident1) }
                coJustRun { behandlendeEnhetClient.getEnhetWithSystemToken(any(), personident2) }
                coJustRun { skjermedePersonerPipClient.getIsSkjermetWithSystemToken(any(), personident1) }
                coJustRun { skjermedePersonerPipClient.getIsSkjermetWithSystemToken(any(), personident2) }
                coJustRun { pdlClient.getPersonWithSystemToken(any(), personident1) }
                coJustRun { pdlClient.getPersonWithSystemToken(any(), personident2) }

                runBlocking {
                    tilgangService.preloadCacheForPersonAccess(
                        callId = callId,
                        personidenter = personidenter,
                    )
                }

                coVerify(exactly = 1) { behandlendeEnhetClient.getEnhetWithSystemToken(callId, personident1) }
                coVerify(exactly = 1) { behandlendeEnhetClient.getEnhetWithSystemToken(callId, personident2) }
                coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithSystemToken(callId, personident1) }
                coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithSystemToken(callId, personident2) }
                coVerify(exactly = 1) { pdlClient.getPersonWithSystemToken(callId, personident1) }
                coVerify(exactly = 1) { pdlClient.getPersonWithSystemToken(callId, personident2) }
            }
        }
    }
})
