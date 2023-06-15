package no.nav.syfo.client.graphapi

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientProxy
import no.nav.syfo.util.bearerHeader
import org.slf4j.LoggerFactory

class GraphApiClient(
    private val azureAdClient: AzureAdClient,
    private val baseUrl: String,
    private val relevantSyfoRoller: List<AdRolle>
) {
    private val httpClient = httpClientProxy()

    suspend fun hasAccess(
        // TODO: legg inn callId
        adRolle: AdRolle,
        token: String,
    ): Boolean {
        val groupList = getRoleList(token = token)

        return isRoleInUserGroupList(
            groupList = groupList.value,
            adRolle = adRolle,
        )
    }

    suspend fun getRoleList(token: String): GraphApiUserGroupsResponse {
        // TODO: sjekk cachen først
        // TODO: add callId for exception handling
        // TODO: add metrics
        val oboToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = baseUrl,
            token = token,
        )?.accessToken ?: throw RuntimeException("Failed to request list of veileder roles")

        val url = "$baseUrl/v1.0/$GRAPHAPI_USER_GROUPS_PATH?\$count=true&$FILTER_QUERY"
        val filter = relevantSyfoRoller.map { it.id }.joinToString(separator = " or ") { "id eq '$it'" }
        val urlWithFilter = "$url$filter"

        return try {
            val response: GraphApiUserGroupsResponse = httpClient.get(urlWithFilter) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header("ConsistencyLevel", "eventual")
                accept(ContentType.Application.Json)
            }.body()
            response
        } catch (e: ResponseException) {
            log.error(
                "Error while trying to fetch veileder user groups from GraphApi {}, {}",
                StructuredArguments.keyValue("statusCode", e.response.status.value.toString()),
                StructuredArguments.keyValue("message", e.message),
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
