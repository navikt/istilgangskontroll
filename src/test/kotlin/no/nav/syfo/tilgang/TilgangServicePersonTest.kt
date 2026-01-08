package no.nav.syfo.tilgang

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetDTO
import no.nav.syfo.client.behandlendeenhet.EnhetDTO
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.norg.NorgClient
import no.nav.syfo.client.norg.domain.NorgEnhet
import no.nav.syfo.client.pdl.*
import no.nav.syfo.client.skjermedepersoner.SkjermedePersonerPipClient
import no.nav.syfo.client.tilgangsmaskin.TilgangsmaskinClient
import no.nav.syfo.domain.Personident
import no.nav.syfo.testhelper.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class TilgangServicePersonTest {
    private val azureAdClient = mockk<AzureAdClient>(relaxed = true)
    private val graphApiClient = mockk<GraphApiClient>(relaxed = true)
    private val skjermedePersonerPipClient = mockk<SkjermedePersonerPipClient>(relaxed = true)
    private val pdlClient = mockk<PdlClient>(relaxed = true)
    private val behandlendeEnhetClient = mockk<BehandlendeEnhetClient>(relaxed = true)
    private val norgClient = mockk<NorgClient>(relaxed = true)
    private val valkeyStore = mockk<ValkeyStore>(relaxed = true)
    private val tilgangsmaskin = mockk<TilgangsmaskinClient>(relaxed = true)
    private val externalMockEnvironment = ExternalMockEnvironment()
    private val adRoller = AdRoller(externalMockEnvironment.environment)

    private val tilgangService = TilgangService(
        graphApiClient = graphApiClient,
        adRoller = adRoller,
        valkeyStore = valkeyStore,
        azureAdClient = azureAdClient,
        skjermedePersonerPipClient = skjermedePersonerPipClient,
        pdlClient = pdlClient,
        behandlendeEnhetClient = behandlendeEnhetClient,
        norgClient = norgClient,
        tilgangsmaskin = tilgangsmaskin,
    )

    private val TWELVE_HOURS_IN_SECONDS = 12 * 60 * 60L
    private val appName = "anyApp"

    private fun verifyCacheSet(exactly: Int, key: String = "", harTilgang: Boolean = true) {
        verify(exactly = exactly) {
            if (exactly == 0) {
                valkeyStore.setObject<Any>(any(), any(), any())
            } else {
                valkeyStore.setObject(
                    key = key,
                    value = Tilgang(erGodkjent = harTilgang),
                    expireSeconds = TWELVE_HOURS_IN_SECONDS
                )
            }
        }
    }

    private val validToken = Token(
        generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT,
        )
    )

    @AfterEach
    fun afterEach() {
        clearMocks(
            graphApiClient,
            valkeyStore,
            skjermedePersonerPipClient,
            pdlClient,
            behandlendeEnhetClient,
            norgClient,
        )
    }

    @Nested
    @DisplayName("has access to SYFO")
    inner class HasAccessToSyfo {
        val personident = Personident(UserConstants.PERSONIDENT)
        val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
        val callId = "123"

        @BeforeEach
        fun beforeEach() {
            coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns true
            coEvery {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    any(),
                    personident,
                    any()
                )
            } returns false
            coEvery { pdlClient.getPerson(any(), personident) } returns getUgradertInnbygger()
        }
    }

    @Nested
    @DisplayName("has geografisk access to person")
    inner class HasGeografiskAccessToPerson {
        val personident = Personident(UserConstants.PERSONIDENT)
        val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
        val callId = "123"

        @BeforeEach
        fun beforeEach() {
            coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
            coEvery {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    any(),
                    personident,
                    any()
                )
            } returns false
            coEvery { pdlClient.getPerson(any(), personident) } returns getUgradertInnbygger()
        }

        @Test
        fun `Return access if veileder has nasjonal tilgang`() {
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns true

            runBlocking {
                val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

                assertTrue(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
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
            coVerify(exactly = 0) { graphApiClient.getEnheterForVeileder(any(), any()) }
            verifyCacheSet(exactly = 1, key = cacheKey)
        }

        @Test
        fun `Return no access if veileder doesn't have national or regional access and not access to innbyggers enhet`() {
            val innbyggerEnhet = createNorgEnhet(UserConstants.ENHET_VEILEDER)
            val veiledersEnhet = Enhet(UserConstants.ENHET_VEILEDER_NO_ACCESS)
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns false
            coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet
            coEvery { graphApiClient.getEnheterForVeileder(any(), any()) } returns listOf(veiledersEnhet)

            runBlocking {
                val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

                assertFalse(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
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
                graphApiClient.getEnheterForVeileder(
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
            verifyCacheSet(exactly = 0, key = cacheKey, harTilgang = false)
        }

        @Test
        fun `Return access if veileder doesn't have national access but has access to innbyggers enhet`() {
            val innbyggerEnhet = createNorgEnhet(UserConstants.ENHET_VEILEDER)
            val veiledersEnhet = Enhet(UserConstants.ENHET_VEILEDER)
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns false
            coEvery { graphApiClient.getEnheterForVeileder(any(), any()) } returns listOf(veiledersEnhet)
            coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet

            runBlocking {
                val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

                assertTrue(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
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
                graphApiClient.getEnheterForVeileder(
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

        @Test
        fun `Return access if veileder doesn't have national access but has access to innbyggers enhet, uses syfobehandlendeenhet when UTLAND GT`() {
            val innbyggerEnhet = BehandlendeEnhetDTO(
                geografiskEnhet = EnhetDTO(
                    enhetId = UserConstants.ENHET_VEILEDER,
                    navn = "enhet",
                ),
                oppfolgingsenhetDTO = null,
            )
            val veiledersEnhet = Enhet(UserConstants.ENHET_VEILEDER)
            coEvery { pdlClient.getPerson(any(), personident) } returns getUgradertInnbyggerWithUtlandGT()
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns false
            coEvery { graphApiClient.getEnheterForVeileder(any(), any()) } returns listOf(veiledersEnhet)
            coEvery {
                behandlendeEnhetClient.getEnhetWithOboToken(
                    any(),
                    personident,
                    any()
                )
            } returns innbyggerEnhet

            runBlocking {
                val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

                assertTrue(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
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
                graphApiClient.getEnheterForVeileder(
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

        @Test
        fun `Return access if veileder doesn't have national or local access but has regional access`() {
            val innbyggerEnhet = createNorgEnhet(UserConstants.ENHET_VEILEDER)
            val veiledersEnhet = Enhet(UserConstants.ENHET_VEILEDER_NO_ACCESS)
            val overordnetEnhet = createNorgEnhet(UserConstants.ENHET_OVERORDNET)
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns false
            coEvery { graphApiClient.hasAccess(adRoller.REGIONAL, any(), any()) } returns true
            coEvery { graphApiClient.getEnheterForVeileder(any(), any()) } returns listOf(veiledersEnhet)
            coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet
            coEvery { norgClient.getOverordnetEnhetListForNAVKontor(any(), any()) } returns listOf(
                overordnetEnhet
            )

            runBlocking {
                val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

                assertTrue(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
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
                graphApiClient.getEnheterForVeileder(
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

        @Test
        fun `Return access if veileder doesn't have national or local access but has regional access and belongs to fylkeskontor`() {
            val innbyggerEnhet = createNorgEnhet(UserConstants.ENHET_INNBYGGER)
            val veiledersEnhet = Enhet(UserConstants.ENHET_OVERORDNET)
            val overordnetEnhet = createNorgEnhet(UserConstants.ENHET_OVERORDNET)
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns false
            coEvery { graphApiClient.hasAccess(adRoller.REGIONAL, any(), any()) } returns true
            coEvery { graphApiClient.getEnheterForVeileder(any(), any()) } returns listOf(veiledersEnhet)
            coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet
            coEvery {
                norgClient.getOverordnetEnhetListForNAVKontor(
                    any(),
                    Enhet(UserConstants.ENHET_INNBYGGER)
                )
            } returns listOf(overordnetEnhet)

            runBlocking {
                val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

                assertTrue(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
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
                graphApiClient.getEnheterForVeileder(
                    callId = callId,
                    token = validToken,
                )
            }
            coVerify(exactly = 1) {
                norgClient.getOverordnetEnhetListForNAVKontor(
                    callId = callId,
                    enhet = Enhet(id = UserConstants.ENHET_INNBYGGER)
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
                    enhet = Enhet(id = UserConstants.ENHET_OVERORDNET)
                )
            }
            verifyCacheSet(exactly = 1, key = cacheKey)
        }
    }

    @Nested
    @DisplayName("has access to skjermede personer")
    inner class HasAccessToSkjermedePersoner {
        val personident = Personident(UserConstants.PERSONIDENT)
        val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
        val callId = "123"

        @BeforeEach
        fun beforeEach() {
            coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
            coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns true
            coEvery { pdlClient.getPerson(any(), personident) } returns getUgradertInnbygger()
        }

        @Test
        fun `Return no access if person is skjermet and veileder doesn't have correct AdRolle`() {
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) } returns true
            coEvery { graphApiClient.hasAccess(adRoller.EGEN_ANSATT, any(), any()) } returns false

            runBlocking {
                val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

                assertFalse(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
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
            verifyCacheSet(exactly = 0, key = cacheKey, harTilgang = false)
        }

        @Test
        fun `return godkjent access if person is skjermet and veileder has correct AdRolle`() {
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) } returns true
            coEvery { graphApiClient.hasAccess(adRoller.EGEN_ANSATT, any(), any()) } returns true

            runBlocking {
                val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

                assertTrue(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
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

    @Nested
    @DisplayName("has access to adressebeskyttede personer")
    inner class HasAccessToAdressebeskyttedePersoner {
        val personident = Personident(UserConstants.PERSONIDENT)
        val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
        val callId = "123"

        @BeforeEach
        fun beforeEach() {
            coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
            coEvery { graphApiClient.hasAccess(adRoller.NASJONAL, any(), any()) } returns true
            coEvery {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    any(),
                    personident,
                    any()
                )
            } returns false
        }

        @Test
        fun `Return no access if person is kode6 and veileder doesn't have correct AdRolle`() {
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
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { pdlClient.getPerson(any(), personident) } returns personWithKode6
            coEvery { graphApiClient.hasAccess(adRoller.KODE6, any(), any()) } returns false

            runBlocking {
                val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

                assertFalse(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
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
            verifyCacheSet(exactly = 0, key = cacheKey, harTilgang = false)
        }

        @Test
        fun `Return no access if person is kode7 and veileder doesn't have correct AdRolle`() {
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { pdlClient.getPerson(any(), personident) } returns getInnbyggerWithKode7()
            coEvery { graphApiClient.hasAccess(adRoller.KODE7, any(), any()) } returns false

            runBlocking {
                val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

                assertFalse(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
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
            verifyCacheSet(exactly = 0, key = cacheKey, harTilgang = false)
        }

        @Test
        fun `return godkjent access if person is kode6 and veileder has correct AdRolle`() {
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { pdlClient.getPerson(any(), personident) } returns getinnbyggerWithKode6()
            coEvery { graphApiClient.hasAccess(adRoller.KODE6, any(), any()) } returns true

            runBlocking {
                val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

                assertTrue(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
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

        @Test
        fun `return godkjent access if person is kode7 and veileder has correct AdRolle`() {
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { pdlClient.getPerson(any(), personident) } returns getInnbyggerWithKode7()
            coEvery { graphApiClient.hasAccess(adRoller.KODE7, any(), any()) } returns true

            runBlocking {
                val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

                assertTrue(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
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

        @Test
        fun `return godkjent access if person doesn't have adressebeskyttelse`() {
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { pdlClient.getPerson(any(), personident) } returns getUgradertInnbygger()

            runBlocking {
                val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

                assertTrue(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
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

    @Test
    fun `return result from cache hit`() {
        val personident = Personident(UserConstants.PERSONIDENT)
        val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
        val callId = "123"
        every { valkeyStore.getObject<Tilgang?>(any()) } returns Tilgang(erGodkjent = true)

        runBlocking {
            val tilgang = tilgangService.checkTilgangToPerson(validToken, personident, callId, appName)

            assertTrue(tilgang.erGodkjent)
        }

        verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
        coVerify(exactly = 0) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) }
        coVerify(exactly = 0) { graphApiClient.hasAccess(any(), any(), any()) }
        verifyCacheSet(exactly = 0)
    }

    @Nested
    @DisplayName("check access to papirsykmelding person")
    inner class CheckAccessToPapirsykmeldingPerson {
        @Test
        fun `gives cached persontilgang to veileder with Papirsykmelding AD group`() {
            val personident = Personident(UserConstants.PERSONIDENT)
            val cacheKey = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
            val callId = "123"
            every { valkeyStore.getObject<Tilgang?>(any()) } returns Tilgang(erGodkjent = true)
            coEvery { graphApiClient.hasAccess(adRoller.PAPIRSYKMELDING, any(), any()) } returns true

            runBlocking {
                val tilgang =
                    tilgangService.checkTilgangToPersonWithPapirsykmelding(validToken, personident, callId, appName)

                assertTrue(tilgang.erGodkjent)
            }

            coVerify(exactly = 1) {
                graphApiClient.hasAccess(
                    adRolle = adRoller.PAPIRSYKMELDING,
                    token = validToken,
                    callId = callId,
                )
            }
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
        }

        @Test
        fun `denies access for veileder without Papirsykmelding AD group`() {
            val personident = Personident(UserConstants.PERSONIDENT)
            val callId = "123"
            coEvery { graphApiClient.hasAccess(adRoller.PAPIRSYKMELDING, any(), any()) } returns false

            runBlocking {
                val tilgang =
                    tilgangService.checkTilgangToPersonWithPapirsykmelding(validToken, personident, callId, appName)

                assertFalse(tilgang.erGodkjent)
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
