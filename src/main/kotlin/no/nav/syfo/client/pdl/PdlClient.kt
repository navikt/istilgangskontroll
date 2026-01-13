package no.nav.syfo.client.pdl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.cache.ValkeyStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.Personident
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class PdlClient(
    private val azureAdClient: AzureAdClient,
    private val baseUrl: String,
    private val clientId: String,
    private val valkeyStore: ValkeyStore,
    private val httpClient: HttpClient = httpClientDefault(),
) {
    suspend fun getPerson(
        callId: String,
        personident: Personident,
    ): PipPersondataResponse {
        val cacheKey = "$PDL_PERSON_CACHE_KEY-$personident"
        val cachedPerson = valkeyStore.getObject<PipPersondataResponse>(key = cacheKey)

        return if (cachedPerson != null) {
            COUNT_CALL_PDL_PERSON_CACHE_HIT.increment()
            cachedPerson
        } else {
            COUNT_CALL_PDL_PERSON_CACHE_MISS.increment()
            getPersonFromPdl(callId, personident).also {
                valkeyStore.setObject(
                    key = cacheKey,
                    value = it,
                    expireSeconds = TWELVE_HOURS_IN_SECS,
                )
            }
        }
    }

    private suspend fun getPersonFromPdl(callId: String, personident: Personident): PipPersondataResponse {
        val token = azureAdClient.getSystemToken(
            scopeClientId = clientId,
            callId = callId,
        )?.accessToken
            ?: throw RuntimeException("Failed to request person info from pdl: Failed to get token from AzureAD with callId=$callId")

        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/v1/person") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, bearerHeader(token))
                header(NAV_CALL_ID_HEADER, callId)
                header(IDENT_HEADER, personident.value)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    COUNT_CALL_PDL_PERSON_SUCCESS.increment()
                    response.body<PipPersondataResponse>()
                }
                else -> {
                    COUNT_CALL_PDL_PERSON_FAIL.increment()
                    log.error("Request with url: $baseUrl failed with reponse code ${response.status.value}")
                    throw RuntimeException("Request with url: $baseUrl failed with reponse code ${response.status.value}")
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            COUNT_CALL_PDL_PERSON_FAIL.increment()
            throw RuntimeException("Caught ClosedReceiveChannelException in PdlClient.person", e)
        } catch (e: ResponseException) {
            COUNT_CALL_PDL_PERSON_FAIL.increment()
            log.error(
                "Error while requesting Person from PersonDataLosningen {}, {}, {}",
                StructuredArguments.keyValue("statusCode", e.response.status.value.toString()),
                StructuredArguments.keyValue("message", e.message),
                callIdArgument(callId),
            )
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PdlClient::class.java)
        const val IDENT_HEADER = "ident"
        const val PDL_PERSON_CACHE_KEY = "pdl-pip-person"
        const val TWELVE_HOURS_IN_SECS = 12 * 60 * 60L
    }
}
