package no.nav.syfo.tilgang

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetDTO
import no.nav.syfo.client.behandlendeenhet.EnhetDTO
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.norg.NorgClient
import no.nav.syfo.client.pdl.GeografiskTilknytning
import no.nav.syfo.client.pdl.GeografiskTilknytningType
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.skjermedepersoner.SkjermedePersonerPipClient
import no.nav.syfo.client.tilgangsmaskin.TilgangsmaskinClient
import no.nav.syfo.domain.Personident
import no.nav.syfo.testhelper.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TilgangServiceTest {
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
        dispatcher = Dispatchers.IO.limitedParallelism(20),
        tilgangsmaskin = tilgangsmaskin,
    )

    private val TWELVE_HOURS_IN_SECONDS = 12 * 60 * 60L

    private fun verifyCacheSet(exactly: Int, key: String = "", harTilgang: Boolean = true) {
        verify(exactly = exactly) {
            valkeyStore.setObject(
                key = key,
                value = Tilgang(erGodkjent = harTilgang),
                expireSeconds = TWELVE_HOURS_IN_SECONDS
            )
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
            skjermedePersonerPipClient,
            pdlClient,
            behandlendeEnhetClient,
            norgClient,
            valkeyStore,
        )
    }

    @Nested
    @DisplayName("Check if veileder has access to SYFO")
    inner class CheckVeilederAccessToSyfo {
        private val cacheKey = "tilgang-til-tjenesten-${UserConstants.VEILEDER_IDENT}"

        @Test
        fun `return result from cache hit`() {
            val callId = "123"
            every { valkeyStore.getObject<Tilgang?>(any()) } returns Tilgang(erGodkjent = true)

            runBlocking {
                tilgangService.checkTilgangToSyfo(validToken, callId)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 0) { graphApiClient.hasAccess(any(), any(), any()) }
            verifyCacheSet(exactly = 0)
        }

        @Test
        fun `cache response from GraphApiClient on cache miss`() {
            val callId = "123"
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.hasAccess(any(), any(), any()) } returns true

            runBlocking {
                tilgangService.checkTilgangToSyfo(validToken, callId)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 1) { graphApiClient.hasAccess(adRoller.SYFO, validToken, callId) }
            verifyCacheSet(exactly = 1, key = cacheKey)
        }
    }

    @Nested
    @DisplayName("Check if veileder has access to enhet")
    inner class CheckVeilederAccessToEnhet {

        @Test
        fun `return has access if enhet is in veileders list from Microsoft Graph API`() {
            val veiledersEnhet = Enhet(UserConstants.ENHET_VEILEDER)
            val cacheKey = "tilgang-til-enhet-${UserConstants.VEILEDER_IDENT}-$veiledersEnhet"
            val callId = "123"
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.getEnheterForVeileder(any(), any()) } returns listOf(veiledersEnhet)
            coEvery { graphApiClient.hasAccess(any(), any(), any()) } returns true

            runBlocking {
                val tilgang = tilgangService.checkTilgangToEnhet(validToken, callId, veiledersEnhet)

                assertTrue(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 1) { graphApiClient.getEnheterForVeileder(validToken, callId) }
            verifyCacheSet(exactly = 1, key = cacheKey)
        }

        @Test
        fun `return no access if enhet is not in veileders list from Microsoft Graph API`() {
            val wantedEnhet = Enhet(UserConstants.ENHET_VEILEDER)
            val actualEnhet = Enhet(UserConstants.ENHET_VEILEDER_NO_ACCESS)
            val cacheKey = "tilgang-til-enhet-${UserConstants.VEILEDER_IDENT}-$wantedEnhet"
            val callId = "123"
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.getEnheterForVeileder(validToken, callId) } returns listOf(actualEnhet)

            runBlocking {
                val tilgang = tilgangService.checkTilgangToEnhet(validToken, callId, wantedEnhet)

                assertFalse(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 1) { graphApiClient.getEnheterForVeileder(validToken, callId) }
            verifyCacheSet(exactly = 0, key = cacheKey, harTilgang = false)
        }

        @Test
        fun `return result from cache hit`() {
            val enhet = Enhet(UserConstants.ENHET_VEILEDER)
            val cacheKey = "tilgang-til-enhet-${UserConstants.VEILEDER_IDENT}-$enhet"
            val callId = "123"
            every { valkeyStore.getObject<Tilgang?>(cacheKey) } returns Tilgang(erGodkjent = true)

            runBlocking {
                val tilgang = tilgangService.checkTilgangToEnhet(validToken, callId, enhet)

                assertTrue(tilgang.erGodkjent)
            }

            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 0) { graphApiClient.hasAccess(any(), any(), any()) }
            coVerify(exactly = 0) { graphApiClient.getEnheterForVeileder(any(), any()) }
            verifyCacheSet(exactly = 0)
        }
    }

    @Nested
    @DisplayName("Filter list of personident based on veileders access")
    inner class FilterPersonidentByVeilederAccess {

        @Test
        fun `remove all identer if veileder is missing SYFO access`() {
            val callId = "123"
            val appName = "anyApp"
            val personident1 = Personident(UserConstants.PERSONIDENT)
            val personident2 = Personident(UserConstants.PERSONIDENT_GRADERT)
            val personidenter = listOf(personident1.value, personident2.value)
            val cacheKey1 = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident1"
            val cacheKey2 = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident2"
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.hasAccess(any(), any(), any()) } returns false

            runBlocking {
                val filteredPersonidenter = tilgangService.filterIdenterByVeilederAccess(
                    callId = callId,
                    token = validToken,
                    personidenter = personidenter,
                    appName = appName,
                )

                assertEquals(0, filteredPersonidenter.size)
            }

            coVerify(exactly = 1) { graphApiClient.hasAccess(adRoller.SYFO, validToken, callId) }
            coVerify(exactly = 0) { behandlendeEnhetClient.getEnhetWithOboToken(any(), personident1, any()) }
            coVerify(exactly = 0) { behandlendeEnhetClient.getEnhetWithOboToken(any(), personident2, any()) }
            coVerify(exactly = 0) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident1, any()) }
            coVerify(exactly = 0) { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident2, any()) }
            coVerify(exactly = 0) { pdlClient.getPerson(any(), personident1) }
            coVerify(exactly = 0) { pdlClient.getPerson(any(), personident2) }
            verifyCacheSet(exactly = 0, key = cacheKey1, harTilgang = false)
            verifyCacheSet(exactly = 0, key = cacheKey2, harTilgang = false)
        }

        @Test
        fun `remove skjermet innbygger when veileder is missing access`() {
            val callId = "123"
            val appName = "anyApp"
            val veiledersEnhet = Enhet(UserConstants.ENHET_VEILEDER)
            val innbyggerEnhet = createNorgEnhet(UserConstants.ENHET_VEILEDER)
            val ugradertInnbygger = getUgradertInnbygger()
            val personident = Personident(UserConstants.PERSONIDENT)
            val personidentSkjermet = Personident(UserConstants.PERSONIDENT_GRADERT)
            val personidenter = listOf(personident.value, personidentSkjermet.value)
            val cacheKeyAccess = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
            val cacheKeySkjermet = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personidentSkjermet"
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
            coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet
            coEvery { graphApiClient.getEnheterForVeileder(any(), any()) } returns listOf(veiledersEnhet)
            coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) } returns false
            coEvery {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    any(),
                    personidentSkjermet,
                    any()
                )
            } returns true
            coEvery { pdlClient.getPerson(any(), personident) } returns ugradertInnbygger
            coEvery { pdlClient.getPerson(any(), personidentSkjermet) } returns ugradertInnbygger
            coEvery { graphApiClient.hasAccess(adRoller.EGEN_ANSATT, any(), any()) } returns false

            runBlocking {
                val filteredPersonidenter = tilgangService.filterIdenterByVeilederAccess(
                    callId = callId,
                    token = validToken,
                    personidenter = personidenter,
                    appName = appName,
                )

                assertEquals(1, filteredPersonidenter.size)
                assertEquals(personident.value, filteredPersonidenter[0])
            }

            coVerify(exactly = 3) { graphApiClient.hasAccess(adRoller.SYFO, validToken, callId) }
            coVerify(exactly = 2) {
                norgClient.getNAVKontorForGT(
                    callId,
                    GeografiskTilknytning(GeografiskTilknytningType.BYDEL, UserConstants.ENHET_VEILEDER_GT)
                )
            }
            coVerify(exactly = 1) {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    callId,
                    personident,
                    validToken
                )
            }
            coVerify(exactly = 1) {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    callId,
                    personidentSkjermet,
                    validToken
                )
            }
            coVerify(exactly = 2) { pdlClient.getPerson(callId, personident) }
            coVerify(exactly = 1) { pdlClient.getPerson(callId, personidentSkjermet) }
            verifyCacheSet(exactly = 1, key = cacheKeyAccess, harTilgang = true)
            verifyCacheSet(exactly = 0, key = cacheKeySkjermet, harTilgang = false)
        }

        @Test
        fun `remove innbyggere when veileder is missing correct access`() {
            val callId = "123"
            val appName = "anyApp"
            val otherBehandlendeEnhet = BehandlendeEnhetDTO(
                geografiskEnhet = EnhetDTO(
                    enhetId = UserConstants.ENHET_VEILEDER_NO_ACCESS,
                    navn = "enhet",
                ),
                oppfolgingsenhetDTO = null,
            )
            val veiledersEnhet = Enhet(UserConstants.ENHET_VEILEDER)
            val innbyggerEnhet = createNorgEnhet(UserConstants.ENHET_VEILEDER)
            val ugradertInnbygger = getUgradertInnbygger()
            val kode6Innbygger = getinnbyggerWithKode6()
            val utlandGTInnbygger = getUgradertInnbyggerWithUtlandGT()
            val personident = Personident(UserConstants.PERSONIDENT)
            val personidentOtherEnhet = Personident(UserConstants.PERSONIDENT_OTHER_ENHET)
            val personidentSkjermet = Personident(UserConstants.PERSONIDENT_SKJERMET)
            val personidentGradert = Personident(UserConstants.PERSONIDENT_GRADERT)
            val personidenter = listOf(
                personident.value,
                personidentOtherEnhet.value,
                personidentSkjermet.value,
                personidentGradert.value
            )
            val cacheKeyAccess = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personident"
            val cacheKeyOtherEnhet = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personidentOtherEnhet"
            val cacheKeySkjermet = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personidentSkjermet"
            val cacheKeyGradert = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$personidentGradert"
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
            coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet
            coEvery {
                behandlendeEnhetClient.getEnhetWithOboToken(
                    any(),
                    personidentOtherEnhet,
                    any()
                )
            } returns otherBehandlendeEnhet
            coEvery { graphApiClient.hasAccess(adRoller.REGIONAL, any(), any()) } returns false
            coEvery { graphApiClient.getEnheterForVeileder(any(), any()) } returns listOf(veiledersEnhet)
            coEvery { skjermedePersonerPipClient.getIsSkjermetWithOboToken(any(), personident, any()) } returns false
            coEvery {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    any(),
                    personidentSkjermet,
                    any()
                )
            } returns true
            coEvery {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    any(),
                    personidentGradert,
                    any()
                )
            } returns false
            coEvery { pdlClient.getPerson(any(), personident) } returns ugradertInnbygger
            coEvery { pdlClient.getPerson(any(), personidentSkjermet) } returns ugradertInnbygger
            coEvery { pdlClient.getPerson(any(), personidentOtherEnhet) } returns utlandGTInnbygger
            coEvery { pdlClient.getPerson(any(), personidentGradert) } returns kode6Innbygger
            coEvery { graphApiClient.hasAccess(adRoller.EGEN_ANSATT, any(), any()) } returns false
            coEvery { graphApiClient.hasAccess(adRoller.KODE6, any(), any()) } returns false

            runBlocking {
                val filteredPersonidenter = tilgangService.filterIdenterByVeilederAccess(
                    callId = callId,
                    token = validToken,
                    personidenter = personidenter,
                    appName = appName,
                )

                assertEquals(1, filteredPersonidenter.size)
                assertEquals(personident.value, filteredPersonidenter[0])
            }

            coVerify(exactly = 5) { graphApiClient.hasAccess(adRoller.SYFO, validToken, callId) }
            coVerify(exactly = 1) { graphApiClient.hasAccess(adRoller.REGIONAL, validToken, callId) }
            coVerify(exactly = 1) { graphApiClient.hasAccess(adRoller.EGEN_ANSATT, validToken, callId) }
            coVerify(exactly = 1) { graphApiClient.hasAccess(adRoller.KODE6, validToken, callId) }
            coVerify(exactly = 1) {
                behandlendeEnhetClient.getEnhetWithOboToken(
                    callId,
                    personidentOtherEnhet,
                    validToken
                )
            }
            coVerify(exactly = 3) {
                norgClient.getNAVKontorForGT(
                    callId,
                    GeografiskTilknytning(GeografiskTilknytningType.BYDEL, UserConstants.ENHET_VEILEDER_GT)
                )
            }
            coVerify(exactly = 1) {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    callId,
                    personident,
                    validToken
                )
            }
            coVerify(exactly = 0) {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    any(),
                    personidentOtherEnhet,
                    any()
                )
            }
            coVerify(exactly = 1) {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    callId,
                    personidentSkjermet,
                    validToken
                )
            }
            coVerify(exactly = 1) {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    callId,
                    personidentGradert,
                    validToken
                )
            }
            coVerify(exactly = 2) { pdlClient.getPerson(callId, personident) }
            coVerify(exactly = 1) { pdlClient.getPerson(any(), personidentOtherEnhet) }
            coVerify(exactly = 1) { pdlClient.getPerson(any(), personidentSkjermet) }
            coVerify(exactly = 2) { pdlClient.getPerson(callId, personidentGradert) }
            verifyCacheSet(exactly = 1, key = cacheKeyAccess, harTilgang = true)
            verifyCacheSet(exactly = 0, key = cacheKeySkjermet, harTilgang = false)
            verifyCacheSet(exactly = 0, key = cacheKeyOtherEnhet, harTilgang = false)
            verifyCacheSet(exactly = 0, key = cacheKeyGradert, harTilgang = false)
        }

        @Test
        fun `Remove invalid personidenter`() {
            val callId = "123"
            val appName = "anyApp"
            val veiledersEnhet = Enhet(UserConstants.ENHET_VEILEDER)
            val innbyggerEnhet = createNorgEnhet(UserConstants.ENHET_VEILEDER)
            val ugradertInnbygger = getUgradertInnbygger()
            val validPersonident = Personident(UserConstants.PERSONIDENT)
            val invalidPersonident = "1234567890"
            val personidenter = listOf(validPersonident.value, invalidPersonident)
            val cacheKeyValidPersonident = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$validPersonident"
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
            coEvery { norgClient.getNAVKontorForGT(any(), any()) } returns innbyggerEnhet
            coEvery { graphApiClient.getEnheterForVeileder(any(), any()) } returns listOf(veiledersEnhet)
            coEvery {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    any(),
                    validPersonident,
                    any()
                )
            } returns false
            coEvery { pdlClient.getPerson(any(), validPersonident) } returns ugradertInnbygger

            runBlocking {
                val filteredPersonidenter = tilgangService.filterIdenterByVeilederAccess(
                    callId = callId,
                    token = validToken,
                    personidenter = personidenter,
                    appName = appName,
                )

                assertEquals(1, filteredPersonidenter.size)
                assertEquals(validPersonident.value, filteredPersonidenter[0])
            }

            coVerify(exactly = 2) { graphApiClient.hasAccess(adRoller.SYFO, validToken, callId) }
            coVerify(exactly = 1) {
                norgClient.getNAVKontorForGT(
                    callId,
                    GeografiskTilknytning(GeografiskTilknytningType.BYDEL, UserConstants.ENHET_VEILEDER_GT)
                )
            }
            coVerify(exactly = 1) {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    callId,
                    validPersonident,
                    validToken
                )
            }
            coVerify(exactly = 2) { pdlClient.getPerson(callId, validPersonident) }
            verifyCacheSet(exactly = 1, key = cacheKeyValidPersonident, harTilgang = true)
        }

        @Test
        fun `Remove personidenter with missing enhet`() {
            val callId = "123"
            val appName = "anyApp"
            val veiledersEnhet = Enhet(UserConstants.ENHET_VEILEDER)
            val ugradertInnbygger = getUgradertInnbygger()
            val validPersonident = Personident(UserConstants.PERSONIDENT)
            val personidenter = listOf(validPersonident.value)
            val cacheKeyValidPersonident = "tilgang-til-person-${UserConstants.VEILEDER_IDENT}-$validPersonident"
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.hasAccess(adRoller.SYFO, any(), any()) } returns true
            coEvery { norgClient.getNAVKontorForGT(any(), any()) } throws RuntimeException("Feil")
            coEvery { graphApiClient.getEnheterForVeileder(any(), any()) } returns listOf(veiledersEnhet)
            coEvery {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    any(),
                    validPersonident,
                    any()
                )
            } returns false
            coEvery { pdlClient.getPerson(any(), validPersonident) } returns ugradertInnbygger

            runBlocking {
                val filteredPersonidenter = tilgangService.filterIdenterByVeilederAccess(
                    callId = callId,
                    token = validToken,
                    personidenter = personidenter,
                    appName = appName,
                )

                assertEquals(0, filteredPersonidenter.size)
            }

            coVerify(exactly = 2) { graphApiClient.hasAccess(adRoller.SYFO, validToken, callId) }
            coVerify(exactly = 1) {
                norgClient.getNAVKontorForGT(
                    callId,
                    GeografiskTilknytning(GeografiskTilknytningType.BYDEL, UserConstants.ENHET_VEILEDER_GT)
                )
            }
            coVerify(exactly = 0) {
                skjermedePersonerPipClient.getIsSkjermetWithOboToken(
                    callId,
                    validPersonident,
                    validToken
                )
            }
            coVerify(exactly = 1) { pdlClient.getPerson(callId, validPersonident) }
            verifyCacheSet(exactly = 0, key = cacheKeyValidPersonident, harTilgang = false)
        }
    }

    @Nested
    @DisplayName("Preload cache for person access")
    inner class PreloadCacheForPersonAccess {

        @Test
        fun `gets data from behandledeEnhet, skjermedePersonerPip and pdl`() {
            val callId = "123"
            val personident = Personident(UserConstants.PERSONIDENT)
            val personidenter = listOf(UserConstants.PERSONIDENT)
            coJustRun { skjermedePersonerPipClient.getIsSkjermetWithSystemToken(any(), personident) }
            coJustRun { pdlClient.getPerson(any(), personident) }

            runBlocking {
                tilgangService.preloadCacheForPersonAccess(
                    callId = callId,
                    personidenter = personidenter,
                )
            }

            coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithSystemToken(callId, personident) }
            coVerify(exactly = 1) { pdlClient.getPerson(callId, personident) }
        }

        @Test
        fun `gets data from behandledeEnhet, skjermedePersonerPip and pdl for each person in list`() {
            val callId = "123"
            val personident1 = Personident(UserConstants.PERSONIDENT)
            val personident2 = Personident(UserConstants.PERSONIDENT_GRADERT)
            val personidenter = listOf(UserConstants.PERSONIDENT, UserConstants.PERSONIDENT_GRADERT)
            coJustRun { skjermedePersonerPipClient.getIsSkjermetWithSystemToken(any(), personident1) }
            coJustRun { skjermedePersonerPipClient.getIsSkjermetWithSystemToken(any(), personident2) }
            coJustRun { pdlClient.getPerson(any(), personident1) }
            coJustRun { pdlClient.getPerson(any(), personident2) }

            runBlocking {
                tilgangService.preloadCacheForPersonAccess(
                    callId = callId,
                    personidenter = personidenter,
                )
            }

            coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithSystemToken(callId, personident1) }
            coVerify(exactly = 1) { skjermedePersonerPipClient.getIsSkjermetWithSystemToken(callId, personident2) }
            coVerify(exactly = 1) { pdlClient.getPerson(callId, personident1) }
            coVerify(exactly = 1) { pdlClient.getPerson(callId, personident2) }
        }
    }
}
