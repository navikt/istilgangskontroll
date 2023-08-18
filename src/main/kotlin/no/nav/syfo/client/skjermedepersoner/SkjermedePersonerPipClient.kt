package no.nav.syfo.client.skjermedepersoner

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientProxy
import no.nav.syfo.domain.Personident
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class SkjermedePersonerPipClient(
    private val azureAdClient: AzureAdClient,
    private val skjermedePersonerUrl: String,
    private val clientId: String,
    private val httpClient: HttpClient = httpClientProxy(),
) {
    private val log = LoggerFactory.getLogger(SkjermedePersonerPipClient::class.java)

    suspend fun isSkjermet(
        callId: String,
        personIdent: Personident,
        token: Token,
    ): Boolean {
        val oboToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = clientId,
            token = token,
            callId = callId
        )?.accessToken
            ?: throw RuntimeException("Failed to request skjerming from SkjermedePersoner: Failed to get token from AzureAD with callId=$callId")

        val skjermet = try {
            val url = "$skjermedePersonerUrl/skjermet"
            val body = SkjermedePersonerRequestDTO(personIdent.value)

            val skjermet: Boolean = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
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
}
