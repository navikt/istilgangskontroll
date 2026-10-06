package no.nav.syfo.tilgang

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.cache.IValkeyStore
import no.nav.syfo.cache.getObject
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.tilgangsmaskin.TilgangsmaskinClient
import no.nav.syfo.client.tilgangsmaskin.TilgangsmaskinTilgang
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Veileder
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class TilgangServicePersonTest {
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
    private val appName = "anyApp"

    private val validToken = Token(
        generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = VEILEDER_IDENT,
        )
    )

    @AfterEach
    fun afterEach() {
        clearMocks(graphApiClient, valkeyStore, tilgangsmaskin)
    }

    private fun veilederWithGrupper(vararg roller: AdRolle) = Veileder(
        veilederident = VEILEDER_IDENT,
        token = validToken,
        adGrupper = roller.map { createGruppeForRole(it) } + createGruppeForEnhet(UserConstants.ENHET_INNBYGGER),
    )

    @Nested
    @DisplayName("check access to person with tilgangsmaskin")
    inner class CheckAccessToPerson {
        val personident = Personident(UserConstants.PERSONIDENT)
        val cacheKey = "tilgang-til-person-$VEILEDER_IDENT-$personident"
        val callId = "123"

        @BeforeEach
        fun beforeEach() {
            every { valkeyStore.getObject<Tilgang?>(any()) } returns null
        }

        @Test
        fun `return access and cache when tilgangsmaskin grants access`() {
            val veileder = veilederWithGrupper(adRoller.SYFO_FULL)
            coEvery { tilgangsmaskin.hasTilgang(validToken, personident, callId) } returns
                TilgangsmaskinTilgang(hasAccess = true)

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) {
                valkeyStore.setObject(
                    key = cacheKey,
                    value = any<Tilgang>(),
                    expireSeconds = TWELVE_HOURS_IN_SECONDS,
                )
            }
        }

        @Test
        fun `return no access and do not cache when tilgangsmaskin denies access`() {
            val veileder = veilederWithGrupper(adRoller.SYFO_FULL)
            coEvery { tilgangsmaskin.hasTilgang(validToken, personident, callId) } returns
                TilgangsmaskinTilgang(hasAccess = false)

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
            }

            assertFalse(tilgang.erGodkjent)
            verify(exactly = 0) { valkeyStore.setObject<Any>(any(), any(), any()) }
        }
    }

    @Test
    fun `return result from cache hit`() {
        val personident = Personident(UserConstants.PERSONIDENT)
        val cacheKey = "tilgang-til-person-$VEILEDER_IDENT-$personident"
        val callId = "123"
        val veileder = veilederWithGrupper(adRoller.SYFO_LEGACY)
        every { valkeyStore.getObject<Tilgang?>(any()) } returns Tilgang(erGodkjent = true)

        val tilgang = runBlocking {
            tilgangService.checkTilgangToPerson(personident, veileder, callId, appName)
        }

        assertTrue(tilgang.erGodkjent)
        verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
        coVerify(exactly = 0) { tilgangsmaskin.hasTilgang(validToken, personident, callId) }
        verify(exactly = 0) { valkeyStore.setObject<Any>(any(), any(), any()) }
    }

    @Nested
    @DisplayName("check access to papirsykmelding person")
    inner class CheckAccessToPapirsykmeldingPerson {
        val personident = Personident(UserConstants.PERSONIDENT)
        val callId = "123"

        @Test
        fun `gives cached persontilgang to veileder with Papirsykmelding AD group`() {
            val cacheKey = "tilgang-til-person-$VEILEDER_IDENT-$personident"
            val veileder = veilederWithGrupper(adRoller.SYFO_LEGACY, adRoller.PAPIRSYKMELDING)
            every { valkeyStore.getObject<Tilgang?>(any()) } returns Tilgang(erGodkjent = true)

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPersonWithPapirsykmelding(personident, veileder, callId, appName)
            }

            assertTrue(tilgang.erGodkjent)
            verify(exactly = 1) { valkeyStore.getObject<Tilgang?>(key = cacheKey) }
            coVerify(exactly = 0) { tilgangsmaskin.hasTilgang(validToken, personident, callId) }
        }

        @Test
        fun `denies access for veileder without Papirsykmelding AD group`() {
            val veileder = veilederWithGrupper(adRoller.SYFO_LEGACY)

            val tilgang = runBlocking {
                tilgangService.checkTilgangToPersonWithPapirsykmelding(personident, veileder, callId, appName)
            }

            assertFalse(tilgang.erGodkjent)
            coVerify(exactly = 0) { tilgangsmaskin.hasTilgang(validToken, personident, callId) }
            verify(exactly = 0) { valkeyStore.setObject<Any>(any(), any(), any()) }
        }
    }
}
