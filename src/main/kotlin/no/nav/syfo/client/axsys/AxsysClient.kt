package no.nav.syfo.client.axsys

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.api.auth.getNAVIdent
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientProxy
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class AxsysClient(
    private val azureAdClient: AzureAdClient,
    baseUrl: String,
    private val clientId: String,
    private val httpClient: HttpClient = httpClientProxy(),
) {
    private val axsysEnhetUrl: String = "$baseUrl$AXSYS_ENHET_BASE_PATH"

    suspend fun getEnheter(token: Token, callId: String): List<AxsysEnhet> {
        val oboToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = clientId,
            token = token,
            callId = callId
        )?.accessToken
            ?: throw RuntimeException("Failed to request list of Veiledere from Axsys: Failed to get token from AzureAD")

        val navIdent = token.getNAVIdent()
        val enheter = try {
            val url = "$axsysEnhetUrl/api/v1/tilgang/$navIdent"

            val enheter: List<AxsysEnhet> = httpClient.get(url) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header(NAV_CALL_ID_HEADER, callId)
                header(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_APP_ID)
                accept(ContentType.Application.Json)
            }.body()
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
        const val AXSYS_ENHET_BASE_PATH = "/api/v1/enhet"

        private val log = LoggerFactory.getLogger(AxsysClient::class.java)
    }
}
