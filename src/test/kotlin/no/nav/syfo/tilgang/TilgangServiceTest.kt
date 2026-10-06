package no.nav.syfo.tilgang

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.cache.IValkeyStore
import no.nav.syfo.cache.getObject
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.tilgangsmaskin.TilgangsmaskinClient
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Veileder
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TilgangServiceTest {
    private val graphApiClient = mockk<GraphApiClient>(relaxed = true)
    private val valkeyStore = mockk<IValkeyStore>(relaxed = true)
    private val tilgangsmaskin = mockk<TilgangsmaskinClient>(relaxed = true)
    private val externalMockEnvironment = ExternalMockEnvironment()
    private val adRoller = AdRoller(externalMockEnvironment.environment)

    private val tilgangService = TilgangService(
        graphApiClient = graphApiClient,
        adRoller = adRoller,
        valkeyStore = valkeyStore,
        tilgangsmaskin = tilgangsmaskin,
    )

    private val TWELVE_HOURS_IN_SECONDS = 12 * 60 * 60L

    private fun verifyCacheSet(exactly: Int, key: String = "", harTilgang: Boolean = true) {
        verify(exactly = exactly) {
            valkeyStore.setObject(
                key = key,
                value = Tilgang(
                    erGodkjent = harTilgang,
                    fullTilgang = true,
                    finnfastlegeTilgang = true,
                    legacyTilgang = false,
                ),
                expireSeconds = TWELVE_HOURS_IN_SECONDS
            )
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
        )
    }

    @Nested
    @DisplayName("Check if veileder has access to SYFO")
    inner class CheckVeilederAccessToSyfo {
        private val cacheKey = "tilgang-til-tjenesten-$VEILEDER_IDENT"

        @Test
        fun `return result from cache hit with tilgang`() {
            val grupper = listOf(createGruppeForRole(adRoller.SYFO_LEGACY), createGruppeForEnhet(UserConstants.ENHET_VEILEDER))
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns Tilgang(erGodkjent = true)

            val tilgang = runBlocking {
                tilgangService.checkTilgangToSyfo(veileder)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            verifyCacheSet(exactly = 0)
        }

        @Test
        fun `cache tilgang on cache miss`() {
            val grupper = listOf(createGruppeForRole(adRoller.SYFO_FULL), createGruppeForEnhet(UserConstants.ENHET_VEILEDER))
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null

            val tilgang = runBlocking {
                tilgangService.checkTilgangToSyfo(veileder)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            verifyCacheSet(exactly = 1, key = cacheKey)
        }

        @Test
        fun `does not cache when no syfo-tilgang`() {
            val grupper = listOf(createGruppeForEnhet(UserConstants.ENHET_VEILEDER))
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null

            val tilgang = runBlocking {
                tilgangService.checkTilgangToSyfo(veileder)
            }

            assertFalse(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            verifyCacheSet(exactly = 0)
        }
    }

    @Nested
    @DisplayName("Check if veileder has access to enhet")
    inner class CheckVeilederAccessToEnhet {

        @Test
        fun `return has access if enhet is in veileders list`() {
            val veiledersEnhet = Enhet(UserConstants.ENHET_VEILEDER)
            val cacheKey = "tilgang-til-enhet-$VEILEDER_IDENT-$veiledersEnhet"
            val grupper = listOf(createGruppeForEnhet(UserConstants.ENHET_VEILEDER), createGruppeForRole(adRoller.SYFO_FULL))
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null

            val tilgang = runBlocking {
                tilgangService.checkTilgangToEnhet(veileder, veiledersEnhet)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            verifyCacheSet(exactly = 1, key = cacheKey)
        }

        @Test
        fun `return no access if enhet is not in veileders enheter, does not cache`() {
            val wantedEnhet = Enhet(UserConstants.ENHET_VEILEDER)
            val cacheKey = "tilgang-til-enhet-$VEILEDER_IDENT-$wantedEnhet"
            val grupper = listOf(createGruppeForEnhet(UserConstants.ENHET_VEILEDER_NO_ACCESS), createGruppeForRole(adRoller.SYFO_LEGACY))
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null

            val tilgang = runBlocking {
                tilgangService.checkTilgangToEnhet(veileder, wantedEnhet)
            }

            assertFalse(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            verifyCacheSet(exactly = 0)
        }

        @Test
        fun `return result from cache hit`() {
            val enhet = Enhet(UserConstants.ENHET_VEILEDER)
            val cacheKey = "tilgang-til-enhet-$VEILEDER_IDENT-$enhet"
            val grupper = listOf(createGruppeForEnhet(UserConstants.ENHET_VEILEDER), createGruppeForRole(adRoller.SYFO_LEGACY))
            val veileder = Veileder(
                veilederident = VEILEDER_IDENT,
                token = validToken,
                adGrupper = grupper,
            )
            every { valkeyStore.getObject<Tilgang?>(cacheKey) } returns Tilgang(erGodkjent = true)

            val tilgang = runBlocking {
                tilgangService.checkTilgangToEnhet(veileder, enhet)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            verifyCacheSet(exactly = 0)
        }
    }

    @Nested
    @DisplayName("Filter list of personident based on veileders access")
    inner class FilterPersonidentByVeilederAccess {

        @Test
        fun `remove all identer if veileder is missing SYFO access`() {
            val callId = "123"
            val personident1 = Personident(UserConstants.PERSONIDENT)
            val personident2 = Personident(UserConstants.PERSONIDENT_GRADERT)
            val personidenter = listOf(personident1.value, personident2.value)
            val cacheKey1 = "tilgang-til-person-$VEILEDER_IDENT-$personident1"
            val cacheKey2 = "tilgang-til-person-$VEILEDER_IDENT-$personident2"
            val grupperUtenSyfo = listOf(createGruppeForEnhet(UserConstants.ENHET_VEILEDER))
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
            coEvery { graphApiClient.getGrupperForVeilederOgCache(any(), any()) } returns grupperUtenSyfo

            runBlocking {
                val filteredPersonidenter = tilgangService.filterIdenterByVeilederAccess(
                    callId = callId,
                    token = validToken,
                    personidenter = personidenter,
                )

                assertEquals(0, filteredPersonidenter.size)
            }

            coVerify(exactly = 1) { graphApiClient.getGrupperForVeilederOgCache(validToken, callId) }
            verifyCacheSet(exactly = 0, key = cacheKey1, harTilgang = false)
            verifyCacheSet(exactly = 0, key = cacheKey2, harTilgang = false)
        }

        @Test
        fun `Remove invalid personidenter`() {
            val callId = "123"
            val validPersonident = Personident(UserConstants.PERSONIDENT)
            val invalidPersonident = "1234567890"
            val personidenter = listOf(validPersonident.value, invalidPersonident)
            val cacheKeyValidPersonident = "tilgang-til-person-$VEILEDER_IDENT-$validPersonident"
            every { valkeyStore.getObjects(any()) } returns mapOf(cacheKeyValidPersonident to null)
            coEvery { graphApiClient.getGrupperForVeilederOgCache(any(), any()) } returns listOf(
                createGruppeForRole(adRoller.SYFO_FULL),
                createGruppeForEnhet(UserConstants.ENHET_VEILEDER)
            )
            coEvery {
                tilgangsmaskin.hasTilgang(validToken, listOf(validPersonident.value), callId)
            } returns listOf(validPersonident.value)

            runBlocking {
                val filteredPersonidenter = tilgangService.filterIdenterByVeilederAccess(
                    callId = callId,
                    token = validToken,
                    personidenter = personidenter,
                )

                assertEquals(1, filteredPersonidenter.size)
                assertEquals(validPersonident.value, filteredPersonidenter[0])
            }

            coVerify(exactly = 1) { graphApiClient.getGrupperForVeilederOgCache(validToken, callId) }
            coVerify(exactly = 1) {
                tilgangsmaskin.hasTilgang(validToken, listOf(validPersonident.value), callId)
            }
            verifyCacheSet(exactly = 1, key = cacheKeyValidPersonident, harTilgang = true)
        }
    }

    @Nested
    @DisplayName("Check tilgang to persons using tilgangsmaskin bulk endpoint")
    inner class CheckTilgangToPersonsWithTilgangsmaskin {
        @Test
        fun `uses bulk endpoint instead of per-person calls`() {
            val personident = Personident(UserConstants.PERSONIDENT)
            val otherPersonident = Personident(UserConstants.PERSONIDENT_GRADERT)
            val personidenter = listOf(personident, otherPersonident)
            val cacheKey1 = "tilgang-til-person-$VEILEDER_IDENT-$personident"
            val cacheKey2 = "tilgang-til-person-$VEILEDER_IDENT-$otherPersonident"

            every { valkeyStore.getObjects(listOf(cacheKey1, cacheKey2)) } returns mapOf(
                cacheKey1 to null,
                cacheKey2 to null,
            )
            coEvery {
                tilgangsmaskin.hasTilgang(validToken, listOf(personident.value, otherPersonident.value), "callId")
            } returns listOf(personident.value)
            coEvery { graphApiClient.getGrupperForVeilederOgCache(any(), any()) } returns listOf(
                createGruppeForRole(adRoller.SYFO_FULL),
            )

            val tilgangMap = runBlocking {
                val veileder = tilgangService.getVeileder(validToken, "callId")
                tilgangService.checkTilgangToPersons(personidenter, veileder, "callId")
            }

            assertTrue(tilgangMap[personident]!!.erGodkjent)
            assertFalse(tilgangMap[otherPersonident]!!.erGodkjent)

            coVerify(exactly = 1) {
                tilgangsmaskin.hasTilgang(validToken, listOf(personident.value, otherPersonident.value), "callId")
            }
            verify(exactly = 1) {
                valkeyStore.setObject(key = cacheKey1, value = any<Tilgang>(), expireSeconds = TWELVE_HOURS_IN_SECONDS)
            }
            verify(exactly = 0) {
                valkeyStore.setObject(key = cacheKey2, value = any<Tilgang>(), expireSeconds = TWELVE_HOURS_IN_SECONDS)
            }
        }

        @Test
        fun `splits persons into chunks of max 1000 per bulk call`() {
            val personidenter = (1..1500).map { Personident(it.toString().padStart(11, '0')) }
            val cacheKeys = personidenter.map { "tilgang-til-person-$VEILEDER_IDENT-$it" }

            every { valkeyStore.getObjects(cacheKeys) } returns cacheKeys.associateWith { null }
            coEvery { tilgangsmaskin.hasTilgang(validToken, any<List<String>>(), "callId") } answers {
                (secondArg() as List<String>)
            }
            coEvery { graphApiClient.getGrupperForVeilederOgCache(any(), any()) } returns listOf(
                createGruppeForRole(adRoller.SYFO_FULL),
            )

            val tilgangMap = runBlocking {
                val veileder = tilgangService.getVeileder(validToken, "callId")
                tilgangService.checkTilgangToPersons(personidenter, veileder, "callId")
            }

            assertEquals(1500, tilgangMap.size)
            assertTrue(tilgangMap.values.all { it.erGodkjent })

            coVerify(exactly = 2) { tilgangsmaskin.hasTilgang(validToken, any<List<String>>(), "callId") }
            coVerify(exactly = 1) { tilgangsmaskin.hasTilgang(validToken, match<List<String>> { it.size == 1000 }, "callId") }
            coVerify(exactly = 1) { tilgangsmaskin.hasTilgang(validToken, match<List<String>> { it.size == 500 }, "callId") }
        }
    }

    @Nested
    @DisplayName("Filter list of personident based on veileders kjerneregler access")
    inner class FilterPersonidentByVeilederKjernereglerAccess {

        @Test
        fun `remove all identer if veileder is missing SYFO access`() {
            val callId = "123"
            val personident1 = Personident(UserConstants.PERSONIDENT)
            val personident2 = Personident(UserConstants.PERSONIDENT_GRADERT)
            val personidenter = listOf(personident1.value, personident2.value)
            coEvery { graphApiClient.getGrupperForVeilederOgCache(any(), any()) } returns listOf(
                createGruppeForEnhet(UserConstants.ENHET_VEILEDER)
            )

            val filteredPersonidenter = runBlocking {
                tilgangService.filterIdenterByVeilederKjernereglerAccess(
                    callId = callId,
                    token = validToken,
                    personidenter = personidenter,
                )
            }

            assertEquals(0, filteredPersonidenter.size)
            coVerify(exactly = 1) { graphApiClient.getGrupperForVeilederOgCache(validToken, callId) }
            coVerify(exactly = 0) { tilgangsmaskin.hasKjerneTilgang(any(), any(), any()) }
        }

        @Test
        fun `removes invalid personidenter before calling tilgangsmaskin`() {
            val callId = "123"
            val validPersonident = Personident(UserConstants.PERSONIDENT)
            val invalidPersonident = "1234567890"
            val personidenter = listOf(validPersonident.value, invalidPersonident)
            coEvery { graphApiClient.getGrupperForVeilederOgCache(any(), any()) } returns listOf(
                createGruppeForRole(adRoller.SYFO_FULL),
                createGruppeForEnhet(UserConstants.ENHET_VEILEDER)
            )
            coEvery {
                tilgangsmaskin.hasKjerneTilgang(validToken, listOf(validPersonident.value), callId)
            } returns listOf(validPersonident.value)

            val filteredPersonidenter = runBlocking {
                tilgangService.filterIdenterByVeilederKjernereglerAccess(
                    callId = callId,
                    token = validToken,
                    personidenter = personidenter,
                )
            }

            assertEquals(1, filteredPersonidenter.size)
            assertEquals(validPersonident.value, filteredPersonidenter[0])
            coVerify(exactly = 1) { tilgangsmaskin.hasKjerneTilgang(validToken, listOf(validPersonident.value), callId) }
        }

        @Test
        fun `only returns personidenter approved by tilgangsmaskin bulk endpoint`() {
            val callId = "123"
            val personident = Personident(UserConstants.PERSONIDENT)
            val otherPersonident = Personident(UserConstants.PERSONIDENT_GRADERT)
            val personidenter = listOf(personident.value, otherPersonident.value)
            coEvery { graphApiClient.getGrupperForVeilederOgCache(any(), any()) } returns listOf(
                createGruppeForRole(adRoller.SYFO_FULL),
                createGruppeForEnhet(UserConstants.ENHET_VEILEDER)
            )
            coEvery {
                tilgangsmaskin.hasKjerneTilgang(validToken, listOf(personident.value, otherPersonident.value), callId)
            } returns listOf(personident.value)

            val filteredPersonidenter = runBlocking {
                tilgangService.filterIdenterByVeilederKjernereglerAccess(
                    callId = callId,
                    token = validToken,
                    personidenter = personidenter,
                )
            }

            assertEquals(1, filteredPersonidenter.size)
            assertEquals(personident.value, filteredPersonidenter[0])
            verifyCacheSet(exactly = 0)
        }

        @Test
        fun `splits persons into chunks of max 1000 per bulk call`() {
            val callId = "123"
            val personidenter = (1..1500).map { it.toString().padStart(11, '0') }
            coEvery { graphApiClient.getGrupperForVeilederOgCache(any(), any()) } returns listOf(
                createGruppeForRole(adRoller.SYFO_FULL),
            )
            coEvery { tilgangsmaskin.hasKjerneTilgang(validToken, any<List<String>>(), callId) } answers {
                (secondArg() as List<String>)
            }

            val filteredPersonidenter = runBlocking {
                tilgangService.filterIdenterByVeilederKjernereglerAccess(
                    callId = callId,
                    token = validToken,
                    personidenter = personidenter,
                )
            }

            assertEquals(1500, filteredPersonidenter.size)
            coVerify(exactly = 2) { tilgangsmaskin.hasKjerneTilgang(validToken, any<List<String>>(), callId) }
            coVerify(exactly = 1) { tilgangsmaskin.hasKjerneTilgang(validToken, match<List<String>> { it.size == 1000 }, callId) }
            coVerify(exactly = 1) { tilgangsmaskin.hasKjerneTilgang(validToken, match<List<String>> { it.size == 500 }, callId) }
        }
    }
}
