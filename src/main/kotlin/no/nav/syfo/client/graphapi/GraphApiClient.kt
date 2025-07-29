package no.nav.syfo.client.graphapi

import com.microsoft.graph.core.tasks.PageIterator
import com.microsoft.graph.models.DirectoryObjectCollectionResponse
import com.microsoft.graph.models.Group
import com.microsoft.graph.serviceclient.GraphServiceClient
import com.microsoft.kiota.ApiException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.api.auth.getNAVIdent
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.azuread.AzureAdToken
import no.nav.syfo.client.httpClientProxy
import no.nav.syfo.tilgang.AdRolle
import no.nav.syfo.tilgang.AdRoller
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.callIdArgument
import org.jetbrains.annotations.VisibleForTesting
import org.slf4j.LoggerFactory
import java.util.*

class GraphApiClient(
    private val azureAdClient: AzureAdClient,
    private val baseUrl: String,
    private val relevantSyfoRoller: List<AdRolle>,
    private val httpClient: HttpClient = httpClientProxy(),
    private val valkeyStore: ValkeyStore,
    private val adRoller: AdRoller,
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
        ).also { roleInUserGroupList ->
            coroutineScope {
                launch {
                    val groupList2 = getGrupperForVeileder(
                        token = token,
                        callId = callId,
                    )
                    val roleInUserGroupList2 = isRoleInUserGroupList(
                        groupList = groupList2,
                        adRolle = adRolle,
                    )

                    if (roleInUserGroupList == roleInUserGroupList2) {
                        log.info("Sammenligning (hasAccess). Gammel: $roleInUserGroupList, ny: $roleInUserGroupList2 er like.")
                    } else {
                        log.warn("Sammenligning (hasAccess). Gammel: $roleInUserGroupList, ny: $roleInUserGroupList2 er ulike.")
                    }
                }
            }
        }
    }

    private suspend fun getRoleList(token: Token, callId: String): List<GraphApiGroup> {
        val navIdent = token.getNAVIdent()
        val cacheKey = "$GRAPHAPI_CACHE_KEY-$navIdent"
        val cachedRoleList = getCachedRoleList(cacheKey)
        return if (cachedRoleList != null) {
            cachedRoleList
        } else {
            getRoleListFromGraphApi(token, callId).also {
                if (isRoleInUserGroupList(it, adRoller.SYFO)) {
                    valkeyStore.setObject(
                        key = cacheKey,
                        value = it,
                        expireSeconds = TWELVE_HOURS_IN_SECS,
                    )
                }
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
        return valkeyStore.getListObject(key = cacheKey)
    }

    private fun isRoleInUserGroupList(
        groupList: List<GraphApiGroup>,
        adRolle: AdRolle,
    ): Boolean {
        return groupList.map { it.id }.contains(adRolle.id)
    }

    suspend fun getGrupperForVeileder(token: Token, callId: String): List<GraphApiGroup> {
        val veilederIdent = token.getNAVIdent()
        val cacheKey = cacheKeyVeilederGrupper(veilederIdent)
        val cachedGroups: List<GraphApiGroup>? = valkeyStore.getListObject(cacheKey)

        val grupper = if (cachedGroups != null) {
            COUNT_CALL_MS_GRAPH_API_GRUPPE_CACHE_HIT.increment()
            cachedGroups
        } else {
            COUNT_CALL_MS_GRAPH_API_GRUPPE_CACHE_MISS.increment()
            getGroupsForVeileder(token, callId)
        }

        return grupper.also {
            if (isRoleInUserGroupList(it, adRoller.SYFO)) {
                valkeyStore.setObject(
                    key = cacheKey,
                    value = it,
                    // TODO: Midlertidig for testing
//                expireSeconds = TWELVE_HOURS_IN_SECS,
                    expireSeconds = 60 * 5,
                )
            }
        }
    }

    suspend fun getGroupsForVeileder(token: Token, callId: String): List<GraphApiGroup> {
        return try {
            getGroupsForVeilederRequest(token, callId)
                .map { it.graphApiGroup() }
                .apply { COUNT_CALL_MS_GRAPH_API_GRUPPE_SUCCESS.increment() }
        } catch (e: Exception) {
            COUNT_CALL_MS_GRAPH_API_GRUPPE_FAIL.increment()
            val additionalInfo = when (e) {
                is ApiException -> ", statusCode=${e.responseStatusCode}"
                else -> ""
            }
            log.error(
                "Error while getting groups for veileder from Microsoft Graph API, callId=$callId$additionalInfo",
                e
            )
            emptyList()
        }
    }

    private fun Group.graphApiGroup(): GraphApiGroup {
        return GraphApiGroup(
            id = this.id,
            displayName = this.displayName,
            mailNickname = null,
        )
    }

    /**
     * @throws com.microsoft.kiota.ApiException
     * @throws Exception
     */
    @VisibleForTesting
    internal suspend fun getGroupsForVeilederRequest(token: Token, callId: String): List<Group> {
        val oboToken = azureAdClient.getOnBehalfOfTokenForGraphApi(
            scopeClientId = baseUrl,
            token = token,
            callId = callId,
        )
            ?: throw RuntimeException("Failed to request list of groups for veileder in Microsoft Graph API: Failed to get system token from AzureAD")

        val graphServiceClient = createGraphServiceClient(azureAdToken = oboToken)
        val directoryObjectCollectionResponse = graphServiceClient.me().memberOf().get { requestConfiguration ->
            requestConfiguration.headers.add("ConsistencyLevel", "eventual")
            requestConfiguration.queryParameters.select =
                arrayOf(
                    "id",
                    "displayName",
                )
            requestConfiguration.queryParameters.count = true
        }

        val groups = mutableListOf<Group>()
        PageIterator.Builder<Group, DirectoryObjectCollectionResponse>()
            .client(graphServiceClient)
            .collectionPage(Objects.requireNonNull(directoryObjectCollectionResponse))
            .collectionPageFactory(DirectoryObjectCollectionResponse::createFromDiscriminatorValue)
            .processPageItemCallback { group -> groups.add(group) }
            .build()
            .iterate()

        return groups
    }

    fun createGraphServiceClient(azureAdToken: AzureAdToken): GraphServiceClient {
        val scopes = "$baseUrl/.default"
        return GraphServiceClient(azureAdToken.toTokenCredential(), scopes)
    }

    companion object {
        const val GRAPHAPI_CACHE_KEY = "graphapi"
        const val MS_GRAPH_API_CACHE_VEILEDER_GRUPPER_PREFIX = "graphapiVeilederGrupper-"
        const val GRAPHAPI_USER_GROUPS_PATH = "/me/memberOf"
        const val FILTER_QUERY = "\$filter="
        private val log = LoggerFactory.getLogger(GraphApiClient::class.java)
        const val TWELVE_HOURS_IN_SECS = 12 * 60 * 60L
        fun cacheKeyVeilederGrupper(veilederIdent: String) = "$MS_GRAPH_API_CACHE_VEILEDER_GRUPPER_PREFIX$veilederIdent"
    }
}
