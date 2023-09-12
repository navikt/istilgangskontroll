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
                val ugradertInnbygger = PdlHentPerson(
                    hentPerson = PdlPerson(
                        adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.UGRADERT)),
                    ),
                )

                beforeEachTest {
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns true
                    coEvery { skjermedePersonerPipClient.isSkjermet(any(), personident, any()) } returns false
                    coEvery { pdlClient.person(any(), personident, any()) } returns ugradertInnbygger
                }

                it("Return no access if veileder doesn't have SYFO access") {
                    every { redisStore.getObject<Tilgang?>(cacheKey) } returns null
                    coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns false

                    runBlocking {
                        val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

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
                        val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

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
                val ugradertInnbygger = PdlHentPerson(
                    hentPerson = PdlPerson(
                        adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.UGRADERT)),
                    ),
                )

                beforeEachTest {
                    coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
                    coEvery { skjermedePersonerPipClient.isSkjermet(any(), personident, any()) } returns false
                    coEvery { pdlClient.person(any(), personident, any()) } returns ugradertInnbygger
                }

                it("Return access if veileder has nasjonal tilgang") {
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns true

                    runBlocking {
                        val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

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
                        behandlendeEnhetClient.getEnhet(any(), personident, any())
                    }
                    coVerify(exactly = 0) {
                        axsysClient.getEnheter(any(), any())
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey)
                }

                it("Return no access if veileder doesn't have national or regional access and not access to innbyggers enhet") {
                    val innbyggerEnhet = BehandlendeEnhetDTO(enhetId = UserConstants.VEILEDER_ENHET, navn = "enhet")
                    val veiledersEnhet = AxsysEnhet(
                        enhetId = UserConstants.VEILEDER_NO_ACCESS_ENHET,
                        navn = "enhet",
                    )
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns false
                    coEvery { behandlendeEnhetClient.getEnhet(any(), personident, any()) } returns innbyggerEnhet
                    coEvery { axsysClient.getEnheter(any(), any()) } returns listOf(veiledersEnhet)

                    runBlocking {
                        val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

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
                    coVerify(exactly = 1) {
                        behandlendeEnhetClient.getEnhet(
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
                        norgClient.getOverordnetEnhetListForNAVKontor(
                            any(),
                            any(),
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey, harTilgang = false)
                }

                it("Return access if veileder doesn't have national access but has access to innbyggers enhet") {
                    val innbyggerEnhet = BehandlendeEnhetDTO(enhetId = UserConstants.VEILEDER_ENHET, navn = "enhet")
                    val veiledersEnhet = AxsysEnhet(
                        enhetId = UserConstants.VEILEDER_ENHET,
                        navn = "enhet",
                    )
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns false
                    coEvery { behandlendeEnhetClient.getEnhet(any(), personident, any()) } returns innbyggerEnhet
                    coEvery { axsysClient.getEnheter(any(), any()) } returns listOf(veiledersEnhet)

                    runBlocking {
                        val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

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
                        behandlendeEnhetClient.getEnhet(
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
                        norgClient.getOverordnetEnhetListForNAVKontor(
                            any(),
                            any(),
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey)
                }

                it("Return access if veileder doesn't have national or local access but has regional access") {
                    val innbyggerEnhet = BehandlendeEnhetDTO(enhetId = UserConstants.VEILEDER_ENHET, navn = "enhet")
                    val veiledersEnhet = AxsysEnhet(
                        enhetId = UserConstants.VEILEDER_NO_ACCESS_ENHET,
                        navn = "enhet",
                    )
                    val overordnetEnhet = createNorgEnhet(UserConstants.OVERORDNET_ENHET)
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns false
                    coEvery { graphApiClient.hasAccess(adRoller.REGIONAL, any(), any()) } returns true
                    coEvery { behandlendeEnhetClient.getEnhet(any(), personident, any()) } returns innbyggerEnhet
                    coEvery { axsysClient.getEnheter(any(), any()) } returns listOf(veiledersEnhet)
                    coEvery { norgClient.getOverordnetEnhetListForNAVKontor(any(), any()) } returns listOf(
                        overordnetEnhet
                    )

                    runBlocking {
                        val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

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
                    coVerify(exactly = 1) {
                        behandlendeEnhetClient.getEnhet(
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
                    coVerify(exactly = 1) {
                        norgClient.getOverordnetEnhetListForNAVKontor(
                            callId = callId,
                            enhet = Enhet(id = UserConstants.VEILEDER_ENHET)
                        )
                    }
                    coVerify(exactly = 1) {
                        norgClient.getOverordnetEnhetListForNAVKontor(
                            callId = callId,
                            enhet = Enhet(id = UserConstants.VEILEDER_NO_ACCESS_ENHET)
                        )
                    }
                    verifyCacheSet(exactly = 1, key = cacheKey)
                }
            }

            describe("has access to skjermede personer") {
                val personident = Personident(UserConstants.PERSONIDENT)
                val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
                val callId = "123"
                val ugradertInnbygger = PdlHentPerson(
                    hentPerson = PdlPerson(
                        adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.UGRADERT)),
                    ),
                )

                beforeEachTest {
                    coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
                    coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns true
                    coEvery { pdlClient.person(any(), personident, any()) } returns ugradertInnbygger
                }

                it("Return no access if person is skjermet and veileder doesn't have correct AdRolle") {
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { skjermedePersonerPipClient.isSkjermet(any(), personident, any()) } returns true
                    coEvery { graphApiClient.hasAccess(adRoller.EGEN_ANSATT, any(), any()) } returns false

                    runBlocking {
                        val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

                        tilgang.erGodkjent shouldBeEqualTo false
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        skjermedePersonerPipClient.isSkjermet(
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
                    coEvery { skjermedePersonerPipClient.isSkjermet(any(), personident, any()) } returns true
                    coEvery { graphApiClient.hasAccess(adRoller.EGEN_ANSATT, any(), any()) } returns true

                    runBlocking {
                        val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

                        tilgang.erGodkjent shouldBeEqualTo true
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        skjermedePersonerPipClient.isSkjermet(
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
                    coEvery { skjermedePersonerPipClient.isSkjermet(any(), personident, any()) } returns false
                }

                it("Return no access if person is kode6 and veileder doesn't have correct AdRolle") {
                    val personWithKode6 = PdlHentPerson(
                        hentPerson = PdlPerson(
                            adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.STRENGT_FORTROLIG)),
                        ),
                    )
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { pdlClient.person(any(), personident, any()) } returns personWithKode6
                    coEvery { graphApiClient.hasAccess(adRoller.KODE6, any(), any()) } returns false

                    runBlocking {
                        val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

                        tilgang.erGodkjent shouldBeEqualTo false
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        pdlClient.person(
                            callId = callId,
                            personident = personident,
                            token = validToken,
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
                    val personWithKode7 = PdlHentPerson(
                        hentPerson = PdlPerson(
                            adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.FORTROLIG)),
                        ),
                    )
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { pdlClient.person(any(), personident, any()) } returns personWithKode7
                    coEvery { graphApiClient.hasAccess(adRoller.KODE7, any(), any()) } returns false

                    runBlocking {
                        val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

                        tilgang.erGodkjent shouldBeEqualTo false
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        pdlClient.person(
                            callId = callId,
                            personident = personident,
                            token = validToken,
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
                    val personWithKode6 = PdlHentPerson(
                        hentPerson = PdlPerson(
                            adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.STRENGT_FORTROLIG)),
                        ),
                    )
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { pdlClient.person(any(), personident, any()) } returns personWithKode6
                    coEvery { graphApiClient.hasAccess(adRoller.KODE6, any(), any()) } returns true

                    runBlocking {
                        val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

                        tilgang.erGodkjent shouldBeEqualTo true
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        pdlClient.person(
                            callId = callId,
                            personident = personident,
                            token = validToken,
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
                    val personWithKode7 = PdlHentPerson(
                        hentPerson = PdlPerson(
                            adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.FORTROLIG)),
                        ),
                    )
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { pdlClient.person(any(), personident, any()) } returns personWithKode7
                    coEvery { graphApiClient.hasAccess(adRoller.KODE7, any(), any()) } returns true

                    runBlocking {
                        val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

                        tilgang.erGodkjent shouldBeEqualTo true
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        pdlClient.person(
                            callId = callId,
                            personident = personident,
                            token = validToken,
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
                    val ugradertInnbygger = PdlHentPerson(
                        hentPerson = PdlPerson(
                            adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.UGRADERT)),
                        ),
                    )
                    every { redisStore.getObject<Tilgang?>(any()) } returns null
                    coEvery { pdlClient.person(any(), personident, any()) } returns ugradertInnbygger

                    runBlocking {
                        val tilgang = tilgangService.hasTilgangToPerson(validToken, personident, callId)

                        tilgang.erGodkjent shouldBeEqualTo true
                    }

                    verify(exactly = 1) { redisStore.getObject<Tilgang?>(key = cacheKey) }
                    coVerify(exactly = 1) {
                        pdlClient.person(
                            callId = callId,
                            personident = personident,
                            token = validToken,
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
