package no.nav.syfo.tilgang

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.axsys.AxsysClient
import no.nav.syfo.client.axsys.AxsysEnhet
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetDTO
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.norg.NorgClient
import no.nav.syfo.client.pdl.*
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

        describe("filter list of personident based on veileders access") {
            it("remove all identer if veileder is missing SYFO access") {
                val callId = "123"
                val personident1 = Personident(UserConstants.PERSONIDENT)
                val personident2 = Personident(UserConstants.PERSONIDENT_GRADERT)
                val personidenter = listOf(personident1.value, personident2.value)
                val cacheKey1 = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident1"
                val cacheKey2 = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident2"
                every { redisStore.getObject<Tilgang?>(any()) } returns null
                coEvery { graphApiClient.hasAccess(any(), any(), any()) } returns false

                runBlocking {
                    val filteredPersonidenter = tilgangService.filterIdenterByVeilederAccess(
                        token = validToken,
                        callId = callId,
                        personidenter = personidenter,
                    )

                    filteredPersonidenter.size shouldBeEqualTo 0
                }

                coVerify(exactly = 2) { graphApiClient.hasAccess(adRoller.SYFO, validToken, callId) }
                coVerify(exactly = 0) { behandlendeEnhetClient.getEnhetWithOboToken(any(), personident1, any()) }
                coVerify(exactly = 0) { behandlendeEnhetClient.getEnhetWithOboToken(any(), personident2, any()) }
                coVerify(exactly = 0) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident1, any()) }
                coVerify(exactly = 0) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident2, any()) }
                coVerify(exactly = 0) { pdlClient.getPersonWithOboToken(any(), personident1, any()) }
                coVerify(exactly = 0) { pdlClient.getPersonWithOboToken(any(), personident2, any()) }
                verifyCacheSet(exactly = 1, key = cacheKey1, harTilgang = false)
                verifyCacheSet(exactly = 1, key = cacheKey2, harTilgang = false)
            }

            it("remove skjermet innbygger when veileder is missing access") {
                val callId = "123"
                val behandlendeEnhet = BehandlendeEnhetDTO(
                    enhetId = UserConstants.ENHET_VEILEDER,
                    navn = "enhet",
                )
                val veiledersEnhet = AxsysEnhet(
                    enhetId = UserConstants.ENHET_VEILEDER,
                    navn = "enhet",
                )
                val ugradertInnbygger = PdlHentPerson(
                    hentPerson = PdlPerson(
                        adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.UGRADERT)),
                    ),
                )
                val personident = Personident(UserConstants.PERSONIDENT)
                val personidentSkjermet = Personident(UserConstants.PERSONIDENT_GRADERT)
                val personidenter = listOf(personident.value, personidentSkjermet.value)
                val cacheKeyAccess = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
                val cacheKeySkjermet = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personidentSkjermet"
                every { redisStore.getObject<Tilgang?>(any()) } returns null
                coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
                coEvery { behandlendeEnhetClient.getEnhetWithOboToken(any(), personident, any()) } returns behandlendeEnhet
                coEvery { behandlendeEnhetClient.getEnhetWithOboToken(any(), personidentSkjermet, any()) } returns behandlendeEnhet
                coEvery { axsysClient.getEnheter(any(), any()) } returns listOf(veiledersEnhet)
                coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) } returns false
                coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personidentSkjermet, any()) } returns true
                coEvery { pdlClient.getPersonWithOboToken(any(), personident, any()) } returns ugradertInnbygger
                coEvery { graphApiClient.hasAccess(adRoller.EGEN_ANSATT, any(), any()) } returns false

                runBlocking {
                    val filteredPersonidenter = tilgangService.filterIdenterByVeilederAccess(
                        token = validToken,
                        callId = callId,
                        personidenter = personidenter,
                    )

                    filteredPersonidenter.size shouldBeEqualTo 1
                    filteredPersonidenter[0] shouldBeEqualTo personident.value
                }

                coVerify(exactly = 2) { graphApiClient.hasAccess(adRoller.SYFO, validToken, callId) }
                coVerify(exactly = 1) { behandlendeEnhetClient.getEnhetWithOboToken(callId, personident, validToken) }
                coVerify(exactly = 1) { behandlendeEnhetClient.getEnhetWithOboToken(callId, personidentSkjermet, validToken) }
                coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(callId, personident, validToken) }
                coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(callId, personidentSkjermet, validToken) }
                coVerify(exactly = 1) { pdlClient.getPersonWithOboToken(callId, personident, validToken) }
                coVerify(exactly = 0) { pdlClient.getPersonWithOboToken(any(), personidentSkjermet, any()) }
                verifyCacheSet(exactly = 1, key = cacheKeyAccess, harTilgang = true)
                verifyCacheSet(exactly = 1, key = cacheKeySkjermet, harTilgang = false)
            }

            it("remove innbyggere when veileder is missing correct access") {
                val callId = "123"
                val behandlendeEnhet = BehandlendeEnhetDTO(
                    enhetId = UserConstants.ENHET_VEILEDER,
                    navn = "enhet",
                )
                val otherBehandlendeEnhet = BehandlendeEnhetDTO(
                    enhetId = UserConstants.ENHET_VEILEDER_NO_ACCESS,
                    navn = "enhet",
                )
                val veiledersEnhet = AxsysEnhet(
                    enhetId = UserConstants.ENHET_VEILEDER,
                    navn = "enhet",
                )
                val ugradertInnbygger = PdlHentPerson(
                    hentPerson = PdlPerson(
                        adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.UGRADERT)),
                    ),
                )
                val kode6Innbygger = PdlHentPerson(
                    hentPerson = PdlPerson(
                        adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.STRENGT_FORTROLIG)),
                    ),
                )
                val personident = Personident(UserConstants.PERSONIDENT)
                val personidentOtherEnhet = Personident(UserConstants.PERSONIDENT_OTHER_ENHET)
                val personidentSkjermet = Personident(UserConstants.PERSONIDENT_SKJERMET)
                val personidentGradert = Personident(UserConstants.PERSONIDENT_GRADERT)
                val personidenter = listOf(personident.value, personidentOtherEnhet.value, personidentSkjermet.value, personidentGradert.value)
                val cacheKeyAccess = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
                val cacheKeyOtherEnhet = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personidentOtherEnhet"
                val cacheKeySkjermet = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personidentSkjermet"
                val cacheKeyGradert = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personidentGradert"
                every { redisStore.getObject<Tilgang?>(any()) } returns null
                coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
                coEvery { behandlendeEnhetClient.getEnhetWithOboToken(any(), personident, any()) } returns behandlendeEnhet
                coEvery { behandlendeEnhetClient.getEnhetWithOboToken(any(), personidentSkjermet, any()) } returns behandlendeEnhet
                coEvery { behandlendeEnhetClient.getEnhetWithOboToken(any(), personidentOtherEnhet, any()) } returns otherBehandlendeEnhet
                coEvery { behandlendeEnhetClient.getEnhetWithOboToken(any(), personidentGradert, any()) } returns behandlendeEnhet
                coEvery { graphApiClient.hasAccess(adRoller.REGIONAL, any(), any()) } returns false
                coEvery { axsysClient.getEnheter(any(), any()) } returns listOf(veiledersEnhet)
                coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) } returns false
                coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personidentSkjermet, any()) } returns true
                coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personidentGradert, any()) } returns false
                coEvery { pdlClient.getPersonWithOboToken(any(), personident, any()) } returns ugradertInnbygger
                coEvery { pdlClient.getPersonWithOboToken(any(), personidentGradert, any()) } returns kode6Innbygger
                coEvery { graphApiClient.hasAccess(adRoller.EGEN_ANSATT, any(), any()) } returns false
                coEvery { graphApiClient.hasAccess(adRoller.KODE6, any(), any()) } returns false

                runBlocking {
                    val filteredPersonidenter = tilgangService.filterIdenterByVeilederAccess(
                        token = validToken,
                        callId = callId,
                        personidenter = personidenter,
                    )

                    filteredPersonidenter.size shouldBeEqualTo 1
                    filteredPersonidenter[0] shouldBeEqualTo personident.value
                }

                coVerify(exactly = 4) { graphApiClient.hasAccess(adRoller.SYFO, validToken, callId) }
                coVerify(exactly = 1) { graphApiClient.hasAccess(adRoller.REGIONAL, validToken, callId) }
                coVerify(exactly = 1) { graphApiClient.hasAccess(adRoller.EGEN_ANSATT, validToken, callId) }
                coVerify(exactly = 1) { graphApiClient.hasAccess(adRoller.KODE6, validToken, callId) }
                coVerify(exactly = 1) { behandlendeEnhetClient.getEnhetWithOboToken(callId, personident, validToken) }
                coVerify(exactly = 1) { behandlendeEnhetClient.getEnhetWithOboToken(callId, personidentOtherEnhet, validToken) }
                coVerify(exactly = 1) { behandlendeEnhetClient.getEnhetWithOboToken(callId, personidentSkjermet, validToken) }
                coVerify(exactly = 1) { behandlendeEnhetClient.getEnhetWithOboToken(callId, personidentGradert, validToken) }
                coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(callId, personident, validToken) }
                coVerify(exactly = 0) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personidentOtherEnhet, any()) }
                coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(callId, personidentSkjermet, validToken) }
                coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(callId, personidentGradert, validToken) }
                coVerify(exactly = 1) { pdlClient.getPersonWithOboToken(callId, personident, validToken) }
                coVerify(exactly = 0) { pdlClient.getPersonWithOboToken(any(), personidentOtherEnhet, any()) }
                coVerify(exactly = 0) { pdlClient.getPersonWithOboToken(any(), personidentSkjermet, any()) }
                coVerify(exactly = 1) { pdlClient.getPersonWithOboToken(callId, personidentGradert, validToken) }
                verifyCacheSet(exactly = 1, key = cacheKeyAccess, harTilgang = true)
                verifyCacheSet(exactly = 1, key = cacheKeySkjermet, harTilgang = false)
                verifyCacheSet(exactly = 1, key = cacheKeyOtherEnhet, harTilgang = false)
                verifyCacheSet(exactly = 1, key = cacheKeyGradert, harTilgang = false)
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
