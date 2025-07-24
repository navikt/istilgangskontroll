package no.nav.syfo.client.norg

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.client.norg.domain.NorgEnhet
import no.nav.syfo.mocks.getMockHttpClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.tilgang.Enhet
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class NorgClientTest {
    private val externalMockEnvironment = ExternalMockEnvironment()
    private val valkeyStore = mockk<ValkeyStore>(relaxed = true)
    private val norgClient = NorgClient(
        baseUrl = externalMockEnvironment.environment.clients.norgUrl,
        valkeyStore = valkeyStore,
        httpClient = getMockHttpClient(env = externalMockEnvironment.environment),
    )

    init {
        every {
            valkeyStore.getListObject<NorgEnhet>(any())
        } returns null
    }

    @Test
    fun `returns overordnetNorgEnhet list if 200 OK from NORG`() {
        val overordnedeEnheter = runBlocking {
            norgClient.getOverordnetEnhetListForNAVKontor(
                callId = UUID.randomUUID().toString(),
                enhet = Enhet(UserConstants.ENHET_VEILEDER)
            )
        }
        assertTrue(overordnedeEnheter.isNotEmpty())
    }

    @Test
    fun `returns empty list if 404 not found from NORG`() {
        val overordnedeEnheter = runBlocking {
            norgClient.getOverordnetEnhetListForNAVKontor(
                callId = UUID.randomUUID().toString(),
                enhet = Enhet(UserConstants.ENHET_OVERORDNET_NOT_FOUND)
            )
        }
        assertTrue(overordnedeEnheter.isEmpty())
    }
}
