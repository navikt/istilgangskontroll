package no.nav.syfo.client.norg

import kotlinx.coroutines.runBlocking
import no.nav.syfo.mocks.getMockHttpClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.tilgang.Enhet
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class NorgClientTest {
    private val externalMockEnvironment = ExternalMockEnvironment()
    private val norgClient = NorgClient(
        baseUrl = externalMockEnvironment.environment.clients.norgUrl,
        valkeyStore = externalMockEnvironment.valkeyStore,
        httpClient = getMockHttpClient(env = externalMockEnvironment.environment),
    )

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
