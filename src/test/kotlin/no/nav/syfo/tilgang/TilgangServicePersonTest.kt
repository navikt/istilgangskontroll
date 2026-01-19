package no.nav.syfo.tilgang

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.cache.ValkeyStore
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
import no.nav.syfo.domain.Veileder
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
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

    private fun verifyCacheSet(exactly: Int, key: String? = null, harTilgang: Boolean = true) {
        verify(exactly = exactly) {
            if (exactly == 0) {
                valkeyStore.setObject<Any>(key ?: any(), any(), any())
            } else {
                valkeyStore.setObject(
                    key = key!!,
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
            navIdent = VEILEDER_IDENT,
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
    @DisplayName("has geografisk access to person")
    inner class HasGeografiskAccessToPerson {
        val personident = Personident(UserConstants.PERSONIDENT)
        val cacheKey = "tilgang-til-person-$VEILEDER_IDENT-$personident"
        val callId = "123"

        @BeforeEach
        fun beforeEach() {
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
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
                createGruppeForRole(adRoller.NASJONAL),
                createGruppeForEnhet(UserConstants.ENHET_VEILEDER),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) }
            coVerify(exactly = 1) { pdlClient.getPerson(any(), personident) }
            coVerify(exactly = 0) { behandlendeEnhetClient.getEnhetWithOboToken(any(), personident, any()) }
            verifyCacheSet(exactly = 1, key = cacheKey)
        }

        @Test
        fun `Return no access if veileder doesn't have nasjonal or regional access and not access to innbyggers enhet`() {
            val innbyggerEnhet = createNorgEnhet(UserConstants.ENHET_VEILEDER)
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertFalse(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 0) { behandlendeEnhetClient.getEnhetWithOboToken(any(), personident, any()) }
            coVerify(exactly = 1) {
                norgClient.getNAVKontorForGT(
                    callId = callId,
                    geografiskTilknytning = GeografiskTilknytning(
                        GeografiskTilknytningType.BYDEL,
                        UserConstants.ENHET_VEILEDER_GT
                    )
                )
            }
            coVerify(exactly = 1) { pdlClient.getPerson(any(), personident) }
            coVerify(exactly = 0) {
                norgClient.getOverordnetEnhetListForNAVKontor(
                    any(),
                    any(),
                )
            }
            verifyCacheSet(exactly = 0)
        }

        @Test
        fun `Return access if veileder doesn't have national access but has access to innbyggers enhet`() {
            val innbyggerEnhet = createNorgEnhet(UserConstants.ENHET_VEILEDER)
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
                createGruppeForEnhet(innbyggerEnhet.enhetNr),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 0) {
                behandlendeEnhetClient.getEnhetWithOboToken(any(), personident, any())
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
            coVerify(exactly = 2) { pdlClient.getPerson(any(), personident) }
            coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) }
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
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
                createGruppeForEnhet(innbyggerEnhet.geografiskEnhet.enhetId),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            coEvery { pdlClient.getPerson(any(), personident) } returns getUgradertInnbyggerWithUtlandGT()
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery {
                behandlendeEnhetClient.getEnhetWithOboToken(
                    any(),
                    personident,
                    any()
                )
            } returns innbyggerEnhet

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 1) {
                behandlendeEnhetClient.getEnhetWithOboToken(
                    callId = callId,
                    personident = personident,
                    token = validToken,
                )
            }
            coVerify(exactly = 0) { norgClient.getNAVKontorForGT(any(), any()) }
            coVerify(exactly = 2) { pdlClient.getPerson(any(), personident) }
            coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) }
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
            val veiledersEnhet = createNorgEnhet(UserConstants.ENHET_VEILEDER_NO_ACCESS)
            val overordnetEnhet = createNorgEnhet(UserConstants.ENHET_OVERORDNET)
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
                createGruppeForRole(adRoller.REGIONAL),
                createGruppeForEnhet(veiledersEnhet.enhetNr),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet
            coEvery { norgClient.getOverordnetEnhetListForNAVKontor(any(), any()) } returns listOf(
                overordnetEnhet
            )

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 0) { behandlendeEnhetClient.getEnhetWithOboToken(any(), personident, any()) }
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
            coVerify(exactly = 2) { pdlClient.getPerson(any(), personident) }
            coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) }
            verifyCacheSet(exactly = 1, key = cacheKey)
        }

        @Test
        fun `Return access if veileder doesn't have national or local access but has regional access and belongs to fylkeskontor`() {
            val innbyggerEnhet = createNorgEnhet(UserConstants.ENHET_INNBYGGER)
            val overordnetEnhet = createNorgEnhet(UserConstants.ENHET_OVERORDNET)
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
                createGruppeForRole(adRoller.REGIONAL),
                createGruppeForEnhet(overordnetEnhet.enhetNr),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet
            coEvery {
                norgClient.getOverordnetEnhetListForNAVKontor(
                    any(),
                    Enhet(UserConstants.ENHET_INNBYGGER)
                )
            } returns listOf(overordnetEnhet)

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 0) {
                behandlendeEnhetClient.getEnhetWithOboToken(any(), personident, any())
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
            coVerify(exactly = 2) { pdlClient.getPerson(any(), personident) }
            coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) }
            verifyCacheSet(exactly = 1, key = cacheKey)
        }
    }

    @Nested
    @DisplayName("has access to skjermede personer")
    inner class HasAccessToSkjermedePersoner {
        val personident = Personident(UserConstants.PERSONIDENT)
        val cacheKey = "tilgang-til-person-$VEILEDER_IDENT-$personident"
        val callId = "123"

        @BeforeEach
        fun beforeEach() {
            coEvery { pdlClient.getPerson(any(), personident) } returns getUgradertInnbygger()
        }

        @Test
        fun `Return no access if person is skjermet and veileder doesn't have correct AdRolle`() {
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
                createGruppeForRole(adRoller.NASJONAL),
                createGruppeForEnhet(UserConstants.ENHET_INNBYGGER),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) } returns true

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertFalse(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 1) {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    callId = callId,
                    personident = personident,
                    token = validToken
                )
            }
            coVerify(exactly = 0) { pdlClient.getPerson(any(), personident) }
            verifyCacheSet(exactly = 0, key = cacheKey, harTilgang = false)
        }

        @Test
        fun `return godkjent access if person is skjermet and veileder has correct AdRolle`() {
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
                createGruppeForRole(adRoller.EGEN_ANSATT),
                createGruppeForRole(adRoller.NASJONAL),
                createGruppeForEnhet(UserConstants.ENHET_INNBYGGER),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) } returns true

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 1) {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    callId = callId,
                    personident = personident,
                    token = validToken
                )
            }
            coVerify(exactly = 1) { pdlClient.getPerson(any(), personident) }
            verifyCacheSet(exactly = 1, key = cacheKey)
        }
    }

    @Nested
    @DisplayName("has access to adressebeskyttede personer")
    inner class HasAccessToAdressebeskyttedePersoner {
        val personident = Personident(UserConstants.PERSONIDENT)
        val cacheKey = "tilgang-til-person-$VEILEDER_IDENT-$personident"
        val callId = "123"

        @BeforeEach
        fun beforeEach() {
            coEvery {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any())
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
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
                createGruppeForRole(adRoller.NASJONAL),
                createGruppeForEnhet(UserConstants.ENHET_INNBYGGER),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { pdlClient.getPerson(any(), personident) } returns personWithKode6

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertFalse(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 1) {
                pdlClient.getPerson(
                    callId = callId,
                    personident = personident,
                )
            }
            verifyCacheSet(exactly = 0, key = cacheKey, harTilgang = false)
        }

        @Test
        fun `Return no access if person is kode7 and veileder doesn't have correct AdRolle`() {
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
                createGruppeForRole(adRoller.NASJONAL),
                createGruppeForEnhet(UserConstants.ENHET_INNBYGGER),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { pdlClient.getPerson(any(), personident) } returns getInnbyggerWithKode7()

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertFalse(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 1) {
                pdlClient.getPerson(
                    callId = callId,
                    personident = personident,
                )
            }
            coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) }
            verifyCacheSet(exactly = 0, key = cacheKey, harTilgang = false)
        }

        @Test
        fun `return godkjent access if person is kode6 and veileder has correct AdRolle`() {
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
                createGruppeForRole(adRoller.KODE6),
                createGruppeForRole(adRoller.NASJONAL),
                createGruppeForEnhet(UserConstants.ENHET_INNBYGGER),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { pdlClient.getPerson(any(), personident) } returns getinnbyggerWithKode6()

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 1) {
                pdlClient.getPerson(
                    callId = callId,
                    personident = personident,
                )
            }
            coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) }
            verifyCacheSet(exactly = 1, key = cacheKey)
        }

        @Test
        fun `return godkjent access if person is kode7 and veileder has correct AdRolle`() {
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
                createGruppeForRole(adRoller.KODE7),
                createGruppeForRole(adRoller.NASJONAL),
                createGruppeForEnhet(UserConstants.ENHET_INNBYGGER),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { pdlClient.getPerson(any(), personident) } returns getInnbyggerWithKode7()

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 1) {
                pdlClient.getPerson(
                    callId = callId,
                    personident = personident,
                )
            }
            coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) }
            verifyCacheSet(exactly = 1, key = cacheKey)
        }

        @Test
        fun `return godkjent access if person doesn't have adressebeskyttelse`() {
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
                createGruppeForRole(adRoller.NASJONAL),
                createGruppeForEnhet(UserConstants.ENHET_INNBYGGER),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { pdlClient.getPerson(any(), personident) } returns getUgradertInnbygger()

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 1) {
                pdlClient.getPerson(
                    callId = callId,
                    personident = personident,
                )
            }
            verifyCacheSet(exactly = 1, key = cacheKey)
        }
    }

    @Test
    fun `return result from cache hit`() {
        val personident = Personident(UserConstants.PERSONIDENT)
        val cacheKey = "tilgang-til-person-$VEILEDER_IDENT-$personident"
        val callId = "123"
        val grupper = listOf(
            createGruppeForRole(adRoller.SYFO),
            createGruppeForEnhet(UserConstants.ENHET_INNBYGGER),
        )
        val veileder = Veileder(
            veilederident = VEILEDER_IDENT,
            token = validToken,
            adGrupper = grupper,
        )
        every { valkeyStore.getObject<Tilgang?>(any()) } returns Tilgang(erGodkjent = true)

        val tilgang = runBlocking {
            tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
        }

        assertTrue(tilgang.erGodkjent)
        verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
        coVerify(exactly = 0) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) }
        coVerify(exactly = 0) { pdlClient.getPerson(any(), personident) }
        verifyCacheSet(exactly = 0)
    }

    @Nested
    @DisplayName("check access to papirsykmelding person")
    inner class CheckAccessToPapirsykmeldingPerson {
        val personident = Personident(UserConstants.PERSONIDENT)
        val callId = "123"

        @Test
        fun `gives cached persontilgang to veileder with Papirsykmelding AD group`() {
            val cacheKey = "tilgang-til-person-$VEILEDER_IDENT-$personident"
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
                createGruppeForRole(adRoller.PAPIRSYKMELDING),
                createGruppeForEnhet(UserConstants.ENHET_INNBYGGER),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns Tilgang(erGodkjent = true)

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPersonWithPapirsykmelding(personident, veileder, callId, appName)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 0) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) }
            coVerify(exactly = 0) { pdlClient.getPerson(any(), personident) }
        }

        @Test
        fun `denies access for veileder without Papirsykmelding AD group`() {
            val grupper = listOf(
                createGruppeForRole(adRoller.SYFO),
            )
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            val tilgang = runBlocking {
                tilgangService.checkTilgangToPersonWithPapirsykmelding(personident, veileder, callId, appName)
            }

            assertFalse(tilgang.erGodkjent)
            coVerify(exactly = 0) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) }
            coVerify(exactly = 0) { pdlClient.getPerson(any(), personident) }
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
