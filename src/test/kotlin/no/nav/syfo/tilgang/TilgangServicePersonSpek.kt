package no.nav.syfo.tilgang

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.axsys.AxsysClient
import no.nav.syfo.client.axsys.AxsysEnhet
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetDTO
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.norg.NorgClient
import no.nav.syfo.client.norg.domain.NorgEnhet
import no.nav.syfo.client.pdl.*
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
        dispatcher = Dispatchers.IO.limitedParallelism(20),
    )

    val TWELVE_HOURS_IN_SECONDS = 12 * 60 * 60L
    val appName = "anyApp"

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
            clearMocks(
                graphApiClient,
                axsysClient,
                redisStore,
                skjermedePersonerPipClient,
                pdlClient,
                behandlendeEnhetClient,
                norgClient,
            )
        }

        describe("has access to person") {
            describe("has access to SYFO") {
                val personident = Personident(UserConstants.PERSONIDENT)
                val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
                val callId = "123"
                beforeEachTest {
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns true
                    coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) } returns false
                    coEvery { pdlClient.getPerson(any(), personident) } returns getUgradertInnbygger()
                }

                it("Return no access if veileder doesn't have SYFO access") {
                    every { redisStore.getObject<Tilgang?>(cacheKey) } returns null
                    coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns false

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo false
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.SYFO,
                            token = validToken,
                            callId = callId,
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey, harTilgang = false)
                }

                it("Return access if veileder has SYFO access") {
                    every { redisStore.getObject<Tilgang?>(cacheKey) } returns null
                    coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo true
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.SYFO,
                            token = validToken,
                            callId = callId,
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey)
                }
            }

            describe("has geografisk access to person") {
                val personident = Personident(UserConstants.PERSONIDENT)
                val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
                val callId = "123"

                beforeEachTest {
                    coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
                    coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) } returns false
                    coEvery { pdlClient.getPerson(any(), personident) } returns getUgradertInnbygger()
                }

                it("Return access if veileder has nasjonal tilgang") {
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns true

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo true
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.NASJONAL,
                            token = validToken,
                            callId = callId,
                        )
                    }
                    coVerify(exactly = 0) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.REGIONAL,
                            any(),
                            any(),
                        )
                    }
                    coVerify(exactly = 0) {
                        behandlendeEnhetClient.getEnhetWithOboToken(any(), personident, any())
                    }
                    coVerify(exactly = 0) {
                        axsysClient.getEnheter(any(), any())
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey)
                }

                it("Return no access if veileder doesn't have national or regional access and not access to innbyggers enhet") {
                    val innbyggerEnhet = createNorgEnhet(UserConstants.ENHET_VEILEDER)
                    val veiledersEnhet = AxsysEnhet(
                        enhetId = UserConstants.ENHET_VEILEDER_NO_ACCESS,
                        navn = "enhet",
                    )
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns false
                    coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet
                    coEvery { axsysClient.getEnheter(any(), any()) } returns listOf(veiledersEnhet)

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo false
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.NASJONAL,
                            token = validToken,
                            callId = callId,
                        )
                    }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.REGIONAL,
                            token = validToken,
                            callId = callId,
                        )
                    }
                    coVerify(exactly = 0) {
                        behandlendeEnhetClient.getEnhetWithOboToken(any(), personident, any())
                    }
                    coVerify(exactly = 1) {
                        axsysClient.getEnheter(
                            callId = callId,
                            token = validToken,
                        )
                    }
                    coVerify(exactly = 1) {
                        norgClient.getNAVKontorForGT(
                            callId = callId,
                            geografiskTilknytning = GeografiskTilknytning(
                                GeografiskTilknytningType.BYDEL,
                                UserConstants.ENHET_VEILEDER_GT
                            )
                        )
                    }
                    coVerify(exactly = 0) {
                        norgClient.getOverordnetEnhetListForNAVKontor(
                            any(),
                            any(),
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey, harTilgang = false)
                }

                it("Return access if veileder doesn't have national access but has access to innbyggers enhet") {
                    val innbyggerEnhet = createNorgEnhet(UserConstants.ENHET_VEILEDER)
                    val veiledersEnhet = AxsysEnhet(
                        enhetId = UserConstants.ENHET_VEILEDER,
                        navn = "enhet",
                    )
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns false
                    coEvery { axsysClient.getEnheter(any(), any()) } returns listOf(veiledersEnhet)
                    coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo true
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.NASJONAL,
                            token = validToken,
                            callId = callId,
                        )
                    }
                    coVerify(exactly = 0) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.REGIONAL,
                            token = validToken,
                            callId = callId,
                        )
                    }
                    coVerify(exactly = 0) {
                        behandlendeEnhetClient.getEnhetWithOboToken(any(), personident, any())
                    }
                    coVerify(exactly = 1) {
                        axsysClient.getEnheter(
                            callId = callId,
                            token = validToken,
                        )
                    }
                    coVerify(exactly = 1) {
                        norgClient.getNAVKontorForGT(
                            callId = callId,
                            geografiskTilknytning = GeografiskTilknytning(
                                GeografiskTilknytningType.BYDEL,
                                UserConstants.ENHET_VEILEDER_GT
                            )
                        )
                    }
                    coVerify(exactly = 0) {
                        norgClient.getOverordnetEnhetListForNAVKontor(
                            any(),
                            any(),
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey)
                }

                it("Return access if veileder doesn't have national access but has access to innbyggers enhet, uses syfobehandlendeenhet when UTLAND GT") {
                    val innbyggerEnhet = BehandlendeEnhetDTO(enhetId = UserConstants.ENHET_VEILEDER, navn = "enhet")
                    val veiledersEnhet = AxsysEnhet(
                        enhetId = UserConstants.ENHET_VEILEDER,
                        navn = "enhet",
                    )
                    coEvery { pdlClient.getPerson(any(), personident) } returns getUgradertInnbyggerWithUtlandGT()
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns false
                    coEvery { axsysClient.getEnheter(any(), any()) } returns listOf(veiledersEnhet)
                    coEvery { behandlendeEnhetClient.getEnhetWithOboToken(any(), personident, any()) } returns innbyggerEnhet

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo true
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.NASJONAL,
                            token = validToken,
                            callId = callId,
                        )
                    }
                    coVerify(exactly = 0) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.REGIONAL,
                            token = validToken,
                            callId = callId,
                        )
                    }
                    coVerify(exactly = 1) {
                        behandlendeEnhetClient.getEnhetWithOboToken(
                            callId = callId,
                            personident = personident,
                            token = validToken,
                        )
                    }
                    coVerify(exactly = 1) {
                        axsysClient.getEnheter(
                            callId = callId,
                            token = validToken,
                        )
                    }
                    coVerify(exactly = 0) {
                        norgClient.getNAVKontorForGT(any(), any())
                    }
                    coVerify(exactly = 0) {
                        norgClient.getOverordnetEnhetListForNAVKontor(
                            any(),
                            any(),
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey)
                }

                it("Return access if veileder doesn't have national or local access but has regional access") {
                    val innbyggerEnhet = createNorgEnhet(UserConstants.ENHET_VEILEDER)
                    val veiledersEnhet = AxsysEnhet(
                        enhetId = UserConstants.ENHET_VEILEDER_NO_ACCESS,
                        navn = "enhet",
                    )
                    val overordnetEnhet = createNorgEnhet(UserConstants.ENHET_OVERORDNET)
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns false
                    coEvery { graphApiClient.hasAccess(adRoller.REGIONAL, any(), any()) } returns true
                    coEvery { axsysClient.getEnheter(any(), any()) } returns listOf(veiledersEnhet)
                    coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet
                    coEvery { norgClient.getOverordnetEnhetListForNAVKontor(any(), any()) } returns listOf(
                        overordnetEnhet
                    )

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo true
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.NASJONAL,
                            token = validToken,
                            callId = callId,
                        )
                    }
                    coVerify(exactly = 0) {
                        behandlendeEnhetClient.getEnhetWithOboToken(any(), personident, any())
                    }
                    coVerify(exactly = 1) {
                        axsysClient.getEnheter(
                            callId = callId,
                            token = validToken,
                        )
                    }
                    coVerify(exactly = 1) {
                        norgClient.getOverordnetEnhetListForNAVKontor(
                            callId = callId,
                            enhet = Enhet(id = UserConstants.ENHET_VEILEDER)
                        )
                    }
                    coVerify(exactly = 1) {
                        norgClient.getNAVKontorForGT(
                            callId = callId,
                            geografiskTilknytning = GeografiskTilknytning(
                                GeografiskTilknytningType.BYDEL,
                                UserConstants.ENHET_VEILEDER_GT
                            )
                        )
                    }
                    coVerify(exactly = 1) {
                        norgClient.getOverordnetEnhetListForNAVKontor(
                            callId = callId,
                            enhet = Enhet(id = UserConstants.ENHET_VEILEDER_NO_ACCESS)
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey)
                }
            }

            describe("has access to skjermede personer") {
                val personident = Personident(UserConstants.PERSONIDENT)
                val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
                val callId = "123"

                beforeEachTest {
                    coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns true
                    coEvery { pdlClient.getPerson(any(), personident) } returns getUgradertInnbygger()
                }

                it("Return no access if person is skjermet and veileder doesn't have correct AdRolle") {
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) } returns true
                    coEvery { graphApiClient.hasAccess(adRoller.EGEN_ANSATT, any(), any()) } returns false

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo false
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                            callId = callId,
                            personIdent = personident,
                            token = validToken
                        )
                    }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.EGEN_ANSATT,
                            token = validToken,
                            callId = callId
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey, harTilgang = false)
                }

                it("return godkjent access if person is skjermet and veileder has correct AdRolle") {
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) } returns true
                    coEvery { graphApiClient.hasAccess(adRoller.EGEN_ANSATT, any(), any()) } returns true

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo true
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                            callId = callId,
                            personIdent = personident,
                            token = validToken
                        )
                    }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.EGEN_ANSATT,
                            token = validToken,
                            callId = callId
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey)
                }
            }

            describe("has access to adressebeskyttede personer") {
                val personident = Personident(UserConstants.PERSONIDENT)
                val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
                val callId = "123"

                beforeEachTest {
                    coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns true
                    coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) } returns false
                }

                it("Return no access if person is kode6 and veileder doesn't have correct AdRolle") {
                    val personWithKode6 = PipPersondataResponse(
                        person = PipPerson(
                            adressebeskyttelse = listOf(
                                PipAdressebeskyttelse(
                                    gradering = Gradering.STRENGT_FORTROLIG,
                                )
                            ),
                            doedsfall = emptyList(),
                        ),
                        geografiskTilknytning = null,
                        identer = PipIdenter(emptyList()),
                    )
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { pdlClient.getPerson(any(), personident) } returns personWithKode6
                    coEvery { graphApiClient.hasAccess(adRoller.KODE6, any(), any()) } returns false

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo false
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        pdlClient.getPerson(
                            callId = callId,
                            personident = personident,
                        )
                    }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.KODE6,
                            token = validToken,
                            callId = callId
                        )
                    }
                    coVerify(exactly = 0) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.KODE7,
                            token = validToken,
                            callId = callId
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey, harTilgang = false)
                }

                it("Return no access if person is kode7 and veileder doesn't have correct AdRolle") {
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { pdlClient.getPerson(any(), personident) } returns getInnbyggerWithKode7()
                    coEvery { graphApiClient.hasAccess(adRoller.KODE7, any(), any()) } returns false

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo false
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        pdlClient.getPerson(
                            callId = callId,
                            personident = personident,
                        )
                    }
                    coVerify(exactly = 0) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.KODE6,
                            token = validToken,
                            callId = callId
                        )
                    }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.KODE7,
                            token = validToken,
                            callId = callId
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey, harTilgang = false)
                }

                it("return godkjent access if person is kode6 and veileder has correct AdRolle") {
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { pdlClient.getPerson(any(), personident) } returns getinnbyggerWithKode6()
                    coEvery { graphApiClient.hasAccess(adRoller.KODE6, any(), any()) } returns true

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo true
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        pdlClient.getPerson(
                            callId = callId,
                            personident = personident,
                        )
                    }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.KODE6,
                            token = validToken,
                            callId = callId
                        )
                    }
                    coVerify(exactly = 0) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.KODE7,
                            token = validToken,
                            callId = callId
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey)
                }

                it("return godkjent access if person is kode7 and veileder has correct AdRolle") {
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { pdlClient.getPerson(any(), personident) } returns getInnbyggerWithKode7()
                    coEvery { graphApiClient.hasAccess(adRoller.KODE7, any(), any()) } returns true

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo true
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        pdlClient.getPerson(
                            callId = callId,
                            personident = personident,
                        )
                    }
                    coVerify(exactly = 0) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.KODE6,
                            token = validToken,
                            callId = callId
                        )
                    }
                    coVerify(exactly = 1) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.KODE7,
                            token = validToken,
                            callId = callId
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey)
                }

                it("return godkjent access if person doesn't have adressebeskyttelse") {
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { pdlClient.getPerson(any(), personident) } returns getUgradertInnbygger()

                    runBlocking {
                        val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                        tilgang.erGodkjent shouldBeEqualTo true
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        pdlClient.getPerson(
                            callId = callId,
                            personident = personident,
                        )
                    }
                    coVerify(exactly = 0) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.KODE6,
                            token = validToken,
                            callId = callId
                        )
                    }
                    coVerify(exactly = 0) {
                        graphApiClient.hasAccess(
                            adRolle = adRoller.KODE7,
                            token = validToken,
                            callId = callId
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey)
                }
            }

            it("return result from cache hit") {
                val personident = Personident(UserConstants.PERSONIDENT)
                val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
                val callId = "123"
                every { redisStore.getObject<Tilgang?>(any()) } returns Tilgang(erGodkjent = true)

                runBlocking {
                    val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName).await()

                    tilgang.erGodkjent shouldBeEqualTo true
                }

                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                coVerify(exactly = 0) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) }
                coVerify(exactly = 0) { graphApiClient.hasAccess(any(), any(), any()) }
                verifyCacheSet(exactly = 0)
            }
        }
        describe("check access to papirsykmelding person") {
            it("gives cached persontilgang to veileder with Papirsykmelding AD group") {
                val personident = Personident(UserConstants.PERSONIDENT)
                val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
                val callId = "123"
                every { redisStore.getObject<Tilgang?>(any()) } returns Tilgang(erGodkjent = true)
                coEvery { graphApiClient.hasAccess(adRoller.PAPIRSYKMELDING, any(), any()) } returns true

                runBlocking {
                    val tilgang = tilgangService.checkTilgangToPersonWithPapirsykmelding(validToken, personident, callId, appName)

                    tilgang.erGodkjent shouldBeEqualTo true
                }

                coVerify(exactly = 1) {
                    graphApiClient.hasAccess(
                        adRolle = adRoller.PAPIRSYKMELDING,
                        token = validToken,
                        callId = callId,
                    )
                }
                verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
            }

            it("denies access for veileder without Papirsykmelding AD group") {
                val personident = Personident(UserConstants.PERSONIDENT)
                val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
                val callId = "123"
                coEvery { graphApiClient.hasAccess(adRoller.PAPIRSYKMELDING, any(), any()) } returns false

                runBlocking {
                    val tilgang = tilgangService.checkTilgangToPersonWithPapirsykmelding(validToken, personident, callId, appName)

                    tilgang.erGodkjent shouldBeEqualTo false
                }

                coVerify(exactly = 1) {
                    graphApiClient.hasAccess(
                        adRolle = adRoller.PAPIRSYKMELDING,
                        token = validToken,
                        callId = callId,
                    )
                }
                verifyCacheSet(exactly = 0)
            }
        }
    }
})

fun createNorgEnhet(enhetNr: String) = NorgEnhet(
    enhetNr = enhetNr,
    navn = "enhet",
    status = "aktiv",
    aktiveringsdato = null,
    antallRessurser = null,
    kanalstrategi = null,
    nedleggelsesdato = null,
    oppgavebehandler = null,
    orgNivaa = null,
    orgNrTilKommunaltNavKontor = null,
    organisasjonsnummer = null,
    sosialeTjenester = null,
    type = null,
    underAvviklingDato = null,
    underEtableringDato = null,
    versjon = null,
)
