package no.nav.syfo.client.behandlendeenhet

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.domain.Personident
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class BehandlendeEnhetClient(
    private val azureAdClient: AzureAdClient,
    private val baseUrl: String,
    private val clientId: String,
    private val redisStore: RedisStore,
    private val httpClient: HttpClient = httpClientDefault(),
) {
    private val behandlendeEnhetUrl = "${baseUrl}$BEHANDLENDEENHET_PATH"

    suspend fun getEnhet(
        callId: String,
        personident: Personident,
        token: Token,
    ): BehandlendeEnhetDTO {
        val cacheKey = "$BEHANDLENDEENHET_CACHE_KEY-$personident"
        val cachedEnhet = getCachedBehandlendeEnhet(cacheKey)

        return if (cachedEnhet != null) {
            cachedEnhet
        } else {
            val behandlendeEnhet = getEnhetFromSyfobehandlendeenhet(callId, personident, token)
            redisStore.setObject(
                key = cacheKey,
                value = behandlendeEnhet,
                expireSeconds = PdlClient.TWELVE_HOURS_IN_SECS
            )
            behandlendeEnhet
        }
    }

    private suspend fun getCachedBehandlendeEnhet(cacheKey: String): BehandlendeEnhetDTO? {
        return redisStore.getObject(key = cacheKey)
    }

    private suspend fun getEnhetFromSyfobehandlendeenhet(
        callId: String,
        personident: Personident,
        token: Token,
    ): BehandlendeEnhetDTO {
        val url = behandlendeEnhetUrl
        val oboToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = clientId,
            token = token,
            callId = callId
        )?.accessToken ?: throw RuntimeException("Failed to request access to Enhet: Failed to get OBO token")
        return try {
            val response: HttpResponse = httpClient.get(url) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header(NAV_CALL_ID_HEADER, callId)
                header(NAV_PERSONIDENT_HEADER, personident.value)
                accept(ContentType.Application.Json)
            }
            if (response.status == HttpStatusCode.NoContent) {
                throw RuntimeException("Failed to get behandlende enhet: Didn't find any enheter for innbygger! callId=$callId")
            } else {
                COUNT_CALL_BEHANDLENDEENHET_SUCCESS.increment()
                response.body()
            }
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e.response, callId)
            throw e
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, callId)
            throw e
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        callId: String,
    ) {
        log.error(
            "Error while requesting BehandlendeEnhet of person from Syfobehandlendeenhet with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        COUNT_CALL_BEHANDLENDEENHET_FAIL.increment()
    }

    companion object {
        const val BEHANDLENDEENHET_PATH = "/api/internad/v2/personident"
        private val log = LoggerFactory.getLogger(BehandlendeEnhetClient::class.java)

        const val BEHANDLENDEENHET_CACHE_KEY = "behandlendeenhet"
        const val TWELVE_HOURS_IN_SECS = 12 * 60 * 60L
    }
}
