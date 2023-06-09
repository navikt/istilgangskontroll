package no.nav.syfo.client.graphapi

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientProxy
import no.nav.syfo.tilgang.AdRolle
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.callIdArgument
import org.slf4j.LoggerFactory

class GraphApiClient(
    private val azureAdClient: AzureAdClient,
    private val baseUrl: String,
    private val relevantSyfoRoller: List<AdRolle>,
    private val httpClient: HttpClient = httpClientProxy(),
) {
    suspend fun hasAccess(
        adRolle: AdRolle,
        token: String,
        callId: String,
    ): Boolean {
        val groupList = getRoleList(
            token = token,
            callId = callId,
        )

        return isRoleInUserGroupList(
            groupList = groupList.value,
            adRolle = adRolle,
        )
    }

    private suspend fun getRoleList(token: String, callId: String): GraphApiUserGroupsResponse {
        val oboToken = azureAdClient.getOnBehalfOfTokenForGraphApi(
            scopeClientId = baseUrl,
            token = token,
            callId = callId,
        )?.accessToken ?: throw RuntimeException("Failed to request list of veileder roles, callId: $callId")

        val url = "$baseUrl/v1.0/$GRAPHAPI_USER_GROUPS_PATH?\$count=true&$FILTER_QUERY"
        val filter = relevantSyfoRoller.map { it.id }.joinToString(separator = " or ") { "id eq '$it'" }
        val filterWhitespaceEncoded = filter.replace(" ", "%20")
        val urlWithFilter = "$url$filterWhitespaceEncoded"

        return try {
            val response: GraphApiUserGroupsResponse = httpClient.get(urlWithFilter) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header("ConsistencyLevel", "eventual")
                accept(ContentType.Application.Json)
            }.body()
            COUNT_CALL_GRAPHAPI_USER_GROUPS_PERSON_SUCCESS.increment()
            response
        } catch (e: ResponseException) {
            COUNT_CALL_GRAPHAPI_USER_GROUPS_PERSON_FAIL.increment()
            log.error(
                "Error while trying to fetch veileder user groups from GraphApi {}, {}, {}",
                StructuredArguments.keyValue("statusCode", e.response.status.value.toString()),
                StructuredArguments.keyValue("message", e.message),
                callIdArgument(callId),
            )
            throw e
        }
    }

    private fun isRoleInUserGroupList(
        groupList: List<GraphApiGroup>,
        adRolle: AdRolle,
    ): Boolean {
        return groupList.map { it.id }.contains(adRolle.id)
    }
    companion object {
        const val GRAPHAPI_USER_GROUPS_PATH = "/me/memberOf"
        const val FILTER_QUERY = "\$filter="
        private val log = LoggerFactory.getLogger(GraphApiClient::class.java)
    }
}
