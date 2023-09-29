package no.nav.syfo.client.axsys

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.api.auth.getNAVIdent
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientProxy
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class AxsysClient(
    private val azureAdClient: AzureAdClient,
    private val axsysUrl: String,
    private val clientId: String,
    private val redisStore: RedisStore,
    private val httpClient: HttpClient = httpClientProxy(),
) {

    suspend fun getEnheter(token: Token, callId: String): List<AxsysEnhet> {
        val navIdent = token.getNAVIdent()
        val cacheKey = "$AXSYS_CACHE_KEY-$navIdent"
        val cachedEnheter = getCachedEnheter(cacheKey)

        return if (!cachedEnheter.isNullOrEmpty()) {
            cachedEnheter
        } else {
            val enheter = getEnheterFromAxsys(token = token, callId = callId)
            redisStore.setObject(
                key = cacheKey,
                value = enheter,
                expireSeconds = TWELVE_HOURS_IN_SECS
            )
            enheter
        }
    }

    private fun getCachedEnheter(cacheKey: String): List<AxsysEnhet>? {
        return redisStore.getListObject(key = cacheKey)
    }

    private suspend fun getEnheterFromAxsys(
        token: Token,
        callId: String,
    ): List<AxsysEnhet> {
        val oboToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = clientId,
            token = token,
            callId = callId
        )?.accessToken
            ?: throw RuntimeException("Failed to request list of Veiledere from Axsys: Failed to get token from AzureAD with callId=$callId")

        val navIdent = token.getNAVIdent()
        val enheter = try {
            val url = "$axsysUrl/api/v1/tilgang/$navIdent"

            val enheter: List<AxsysEnhet> = httpClient.get(url) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header(NAV_CALL_ID_HEADER, callId)
                header(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_APP_ID)
                accept(ContentType.Application.Json)
            }.body<AxsysTilgangerResponse>().enheter
            COUNT_CALL_AXSYS_TILGANGER_SUCCESS.increment()
            enheter
        } catch (e: ResponseException) {
            COUNT_CALL_AXSYS_TILGANGER_FAIL.increment()
            log.error(
                "Error while requesting veiledertilganger from Axsys {}, {}, {}",
                StructuredArguments.keyValue("statusCode", e.response.status.value.toString()),
                StructuredArguments.keyValue("message", e.message),
                callIdArgument(callId),
            )
            throw e
        }
        return enheter
    }

    companion object {
        private val log = LoggerFactory.getLogger(AxsysClient::class.java)

        const val AXSYS_CACHE_KEY = "axsys"
        const val TWELVE_HOURS_IN_SECS = 12 * 60 * 60L
    }
}
