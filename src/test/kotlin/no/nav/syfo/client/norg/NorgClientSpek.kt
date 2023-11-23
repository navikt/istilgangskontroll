package no.nav.syfo.client.norg

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.norg.domain.NorgEnhet
import no.nav.syfo.mocks.getMockHttpClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.tilgang.Enhet
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldNotBeEmpty
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

class NorgClientSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment()
    val redisStore = mockk<RedisStore>(relaxed = true)
    val norgClient = NorgClient(
        baseUrl = externalMockEnvironment.environment.clients.norgUrl,
        redisStore = redisStore,
        httpClient = getMockHttpClient(env = externalMockEnvironment.environment),
    )

    describe("NorgClient") {
        describe("getOverordnetEnhetListForNAVKontor") {
            every {
                redisStore.getListObject<NorgEnhet>(any())
            } returns null

            it("returns overordnetNorgEnhet list if 200 OK from NORG") {
                val overordnedeEnheter = runBlocking {
                    norgClient.getOverordnetEnhetListForNAVKontor(
                        callId = UUID.randomUUID().toString(),
                        enhet = Enhet(UserConstants.ENHET_VEILEDER)
                    )
                }
                overordnedeEnheter.shouldNotBeEmpty()
            }

            it("returns empty list if 404 not found from NORG") {
                val overordnedeEnheter = runBlocking {
                    norgClient.getOverordnetEnhetListForNAVKontor(
                        callId = UUID.randomUUID().toString(),
                        enhet = Enhet(UserConstants.ENHET_OVERORDNET_NOT_FOUND)
                    )
                }
                overordnedeEnheter.shouldBeEmpty()
            }
        }
    }
})
