package no.nav.syfo.client.tilgangsmaskin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
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
    private val tilgangsmaskinBulkUrl: String = "$baseUrl/api/v1/bulk/obo/"

    suspend fun hasTilgang(
        token: Token,
        personident: Personident,
        callId: String,
    ): TilgangsmaskinTilgang {
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
                accept(ContentType.Application.Json)
            }
        } catch (exc: ClientRequestException) {
            exc.response
        }
        val hasAccess = response.status == HttpStatusCode.NoContent
        return TilgangsmaskinTilgang(
            hasAccess = hasAccess,
            problemDetailResponse = if (hasAccess) null else response.body() as ProblemDetailResponse?,
        )
    }

    suspend fun hasTilgang(
        token: Token,
        personidenter: List<String>,
        callId: String,
    ): List<String> {
        val oboToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = clientId,
            token = token,
            callId = callId
        ) ?: throw RuntimeException("Could not get oboToken from AzureAd")

        val response = try {
            httpClient.post(tilgangsmaskinBulkUrl) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken.accessToken))
                header(NAV_CALL_ID_HEADER, callId)
                setBody(personidenter.map { personident -> TilgangsmaskinBulkRequest(personident) })
                accept(ContentType.Application.Json)
            }
        } catch (exc: ClientRequestException) {
            exc.response
        }
        return if (response.status == HttpStatusCode.MultiStatus) {
            val tilgangsmaskinBulk = response.body<TilgangsmaskinBulkResponse>()
            return tilgangsmaskinBulk.resultater.filter {
                it.status == HttpStatusCode.NoContent.value
            }.map {
                it.brukerId
            }
        } else {
            throw RuntimeException("Unexpected response from tilgangsmaskin bulk endpoint: ${response.status}")
        }
    }
}

data class TilgangsmaskinTilgang(
    val hasAccess: Boolean,
    val problemDetailResponse: ProblemDetailResponse? = null,
)

data class TilgangsmaskinBulkRequest(
    val brukerId: String,
)

data class TilgangsmaskinBulkResponse(
    val resultater: List<TilgangsmaskinBulkResultat>,
)

data class TilgangsmaskinBulkResultat(
    val brukerId: String,
    val status: Int,
)
