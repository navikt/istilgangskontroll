package no.nav.syfo.client.norg

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.norg.domain.NorgEnhet
import no.nav.syfo.client.pdl.GeografiskTilknytning
import no.nav.syfo.tilgang.Enhet
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import org.slf4j.LoggerFactory.getLogger

class NorgClient(
    private val baseUrl: String,
    private val valkeyStore: ValkeyStore,
    private val httpClient: HttpClient = httpClientDefault(),
) {

    suspend fun getOverordnetEnhetListForNAVKontor(
        callId: String,
        enhet: Enhet,
    ): List<NorgEnhet> {
        val cacheKey = "$NORG_OVERORDNEDE_ENHETER_CACHE_KEY-$enhet"
        val cachedEnheter = valkeyStore.getListObject<NorgEnhet>(key = cacheKey)

        return if (!cachedEnheter.isNullOrEmpty()) {
            cachedEnheter
        } else {
            getOverordnedeEnheter(
                callId = callId,
                enhet = enhet,
            ).also {
                valkeyStore.setObject(
                    key = cacheKey,
                    value = it,
                    expireSeconds = TWELVE_HOURS_IN_SECS,
                )
            }
        }
    }

    private suspend fun getOverordnedeEnheter(
        callId: String,
        enhet: Enhet,
    ): List<NorgEnhet> {
        val url = getOverordnetEnheterForNAVKontorUrl(enhet.id)
        try {
            val response: List<NorgEnhet> = httpClient.get(url) {
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }.body()

            if (response.isEmpty()) {
                log.error("No overordnede enheter returned from NORG2 for enhet $enhet, callId=$callId")
                COUNT_CALL_NORG_ENHET_FAIL.increment()
                throw RuntimeException("No overordnede enheter returned from NORG2 for enhet $enhet, callId=$callId")
            }

            COUNT_CALL_NORG_ENHET_SUCCESS.increment()
            return response
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                COUNT_CALL_NORG_ENHET_NOT_FOUND.increment()
                return emptyList()
            } else {
                val message = "Call to NORG2 for overordnet enhet failed with status HTTP-${e.response.status} for enhet $enhet, callId=$callId"
                log.error(message)
                COUNT_CALL_NORG_ENHET_FAIL.increment()
                throw e
            }
        }
    }

    suspend fun getNAVKontorForGT(
        callId: String,
        geografiskTilknytning: GeografiskTilknytning,
    ): NorgEnhet {
        val cacheKey = "$NORG_GEOGRAFISK_ENHET_CACHE_KEY-${geografiskTilknytning.value}"
        val cachedEnhet = valkeyStore.getObject<NorgEnhet>(key = cacheKey)

        return if (cachedEnhet != null) {
            cachedEnhet
        } else {
            getGeografiskEnhet(
                callId = callId,
                geografiskTilknytning = geografiskTilknytning,
            ).also {
                valkeyStore.setObject(
                    key = cacheKey,
                    value = it,
                    expireSeconds = TWELVE_HOURS_IN_SECS,
                )
            }
        }
    }

    private suspend fun getGeografiskEnhet(callId: String, geografiskTilknytning: GeografiskTilknytning): NorgEnhet {
        try {
            val response: NorgEnhet = httpClient.get(getNAVKontorForGTUrl(geografiskTilknytning)) {
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }.body()

            COUNT_CALL_NAV_KONTOR_FOR_GT_SUCCESS.increment()
            return response
        } catch (e: ResponseException) {
            COUNT_CALL_NAV_KONTOR_FOR_GT_FAIL.increment()
            log.error("Call to NORG2-NAVkontorForGT failed with status HTTP-${e.response.status} for GeografiskTilknytning $geografiskTilknytning")
            throw e
        }
    }

    private fun getOverordnetEnheterForNAVKontorUrl(enhetNr: String): String {
        return "$baseUrl/norg2/api/v1/enhet/$enhetNr/overordnet?organiseringsType=$ORGANISERINGSTYPE"
    }

    private fun getNAVKontorForGTUrl(geografiskTilknytning: GeografiskTilknytning): String {
        return "$baseUrl/norg2/api/v1/enhet/navkontor/${geografiskTilknytning.value}"
    }

    companion object {
        private val log = getLogger(NorgClient::class.java)

        private const val ORGANISERINGSTYPE = "FYLKE"

        const val TWELVE_HOURS_IN_SECS = 12 * 60 * 60L
        private const val NORG_CACHE_KEY = "norg"
        const val NORG_OVERORDNEDE_ENHETER_CACHE_KEY = "$NORG_CACHE_KEY-overordnede-enheter"
        const val NORG_GEOGRAFISK_ENHET_CACHE_KEY = "$NORG_CACHE_KEY-geografisk-enhet"
    }
}
