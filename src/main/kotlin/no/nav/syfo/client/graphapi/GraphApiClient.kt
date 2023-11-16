package no.nav.syfo.client.graphapi

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.api.auth.getNAVIdent
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.axsys.AxsysClient
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
    private val redisStore: RedisStore,
) {
    suspend fun hasAccess(
        adRolle: AdRolle,
        token: Token,
        callId: String,
    ): Boolean {
        val groupList = getRoleList(
            token = token,
            callId = callId,
        )

        return isRoleInUserGroupList(
            groupList = groupList,
            adRolle = adRolle,
        )
    }

    private suspend fun getRoleList(token: Token, callId: String): List<GraphApiGroup> {
        val navIdent = token.getNAVIdent()
        val cacheKey = "$GRAPHAPI_CACHE_KEY-$navIdent"
        val cachedRoleList = getCachedRoleList(cacheKey)
        return if (cachedRoleList != null) {
            cachedRoleList
        } else {
            getRoleListFromGraphApi(token, callId).also {
                redisStore.setObject(
                    key = cacheKey,
                    value = it,
                    expireSeconds = AxsysClient.TWELVE_HOURS_IN_SECS,
                )
            }
        }
    }

    private suspend fun getRoleListFromGraphApi(token: Token, callId: String): List<GraphApiGroup> {
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
            response.value
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

    private fun getCachedRoleList(cacheKey: String): List<GraphApiGroup>? {
        return redisStore.getListObject(key = cacheKey)
    }

    private fun isRoleInUserGroupList(
        groupList: List<GraphApiGroup>,
        adRolle: AdRolle,
    ): Boolean {
        return groupList.map { it.id }.contains(adRolle.id)
    }

    companion object {
        const val GRAPHAPI_CACHE_KEY = "graphapi"
        const val GRAPHAPI_USER_GROUPS_PATH = "/me/memberOf"
        const val FILTER_QUERY = "\$filter="
        private val log = LoggerFactory.getLogger(GraphApiClient::class.java)
    }
}
