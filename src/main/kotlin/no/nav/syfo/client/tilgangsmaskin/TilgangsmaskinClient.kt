package no.nav.syfo.client.tilgangsmaskin

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientProxy
import no.nav.syfo.domain.Personident
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.bearerHeader

class TilgangsmaskinClient(
    private val azureAdClient: AzureAdClient,
    private val baseUrl: String,
    private val clientId: String,
    private val httpClient: HttpClient = httpClientProxy(),
) {
    private val tilgangsmaskinUrl: String = "$baseUrl/api/v1/komplett"

    suspend fun hasTilgang(
        token: Token,
        personident: Personident,
        callId: String,
    ): Boolean {
        val oboToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = clientId,
            token = token,
            callId = callId
        ) ?: throw RuntimeException("Could not get oboToken from AzureAd")

        val response = try {
            httpClient.post(tilgangsmaskinUrl) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken.accessToken))
                header(NAV_CALL_ID_HEADER, callId)
                setBody(personident.value)
            }
        } catch (exc: ClientRequestException) {
            exc.response
        }
        return response.status == HttpStatusCode.NoContent
    }
}
