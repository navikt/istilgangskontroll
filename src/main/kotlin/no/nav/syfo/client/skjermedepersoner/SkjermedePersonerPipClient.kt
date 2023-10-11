package no.nav.syfo.client.skjermedepersoner

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientProxy
import no.nav.syfo.domain.Personident
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class SkjermedePersonerPipClient(
    private val azureAdClient: AzureAdClient,
    private val skjermedePersonerUrl: String,
    private val clientId: String,
    private val redisStore: RedisStore,
    private val httpClient: HttpClient = httpClientProxy(),
) {
    private val log = LoggerFactory.getLogger(SkjermedePersonerPipClient::class.java)

    suspend fun getIsSkjermetWithOboToken(
        callId: String,
        personIdent: Personident,
        token: Token?,
    ) = isSkjermet(
        callId = callId,
        personIdent = personIdent,
        token = token,
    )

    suspend fun getIsSkjermetWithSystemToken(
        callId: String,
        personIdent: Personident,
    ) = isSkjermet(
        callId = callId,
        personIdent = personIdent,
        token = null,
    )

    private suspend fun isSkjermet(
        callId: String,
        personIdent: Personident,
        token: Token?,
    ): Boolean {
        val cacheKey = "$SKJERMEDE_PERSONER_CACHE_KEY-$personIdent"
        val cachedSkjerming = getCachedSkjerming(cacheKey)

        return if (cachedSkjerming != null) {
            cachedSkjerming
        } else {
            val enheter = getSkjermingFromSkjermedePersoner(
                callId = callId,
                personIdent = personIdent,
                token = token,
            )

            redisStore.setObject(
                key = cacheKey,
                value = enheter,
                expireSeconds = TWELVE_HOURS_IN_SECS,
            )
            enheter
        }
    }

    private fun getCachedSkjerming(cacheKey: String): Boolean? {
        return redisStore.getObject(key = cacheKey)
    }

    private suspend fun getSkjermingFromSkjermedePersoner(
        callId: String,
        personIdent: Personident,
        token: Token?,
    ): Boolean {
        val newToken = if (token == null) {
            azureAdClient.getSystemToken(
                scopeClientId = clientId,
                callId = callId,
            )
        } else {
            azureAdClient.getOnBehalfOfToken(
                scopeClientId = clientId,
                token = token,
                callId = callId,
            )
        }?.accessToken
            ?: throw RuntimeException("Failed to request skjerming from SkjermedePersoner: Failed to get token from AzureAD with callId=$callId")

        val skjermet = try {
            val url = "$skjermedePersonerUrl/skjermet"
            val body = SkjermedePersonerRequestDTO(personIdent.value)

            val skjermet: Boolean = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
                header(HttpHeaders.Authorization, bearerHeader(newToken))
                header(NAV_CALL_ID_HEADER, callId)
                header(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_APP_ID)
                accept(ContentType.Application.Json)
            }.body<Boolean>()
            COUNT_CALL_SKJERMEDE_PERSONER_SUCCESS.increment()
            skjermet
        } catch (e: ResponseException) {
            COUNT_CALL_SKJERMEDE_PERSONER_FAIL.increment()
            log.error(
                "Error while requesting skjerming from SkjermedePersoner {}, {}, {}",
                StructuredArguments.keyValue("statusCode", e.response.status.value.toString()),
                StructuredArguments.keyValue("message", e.message),
                callIdArgument(callId),
            )
            throw e
        }
        return skjermet
    }

    companion object {
        private val log = LoggerFactory.getLogger(SkjermedePersonerPipClient::class.java)

        const val SKJERMEDE_PERSONER_CACHE_KEY = "skjermedePersoner"
        const val TWELVE_HOURS_IN_SECS = 12 * 60 * 60L
    }
}
