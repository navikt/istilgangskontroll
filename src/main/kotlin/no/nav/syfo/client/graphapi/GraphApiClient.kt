package no.nav.syfo.client.graphapi

import com.microsoft.graph.core.tasks.PageIterator
import com.microsoft.graph.models.DirectoryObjectCollectionResponse
import com.microsoft.graph.models.Group
import com.microsoft.graph.serviceclient.GraphServiceClient
import com.microsoft.kiota.ApiException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.api.auth.getNAVIdent
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.azuread.AzureAdToken
import no.nav.syfo.tilgang.AdRolle
import no.nav.syfo.tilgang.AdRoller
import no.nav.syfo.tilgang.Enhet
import org.jetbrains.annotations.VisibleForTesting
import org.slf4j.LoggerFactory
import java.util.*

class GraphApiClient(
    private val azureAdClient: AzureAdClient,
    private val baseUrl: String,
    private val valkeyStore: ValkeyStore,
    private val adRoller: AdRoller,
) {
    suspend fun hasAccess(
        adRolle: AdRolle,
        token: Token,
        callId: String,
    ): Boolean {
        val grupper = getGrupperForVeilederOgCache(
            token = token,
            callId = callId,
        )

        return isRoleInUserGroupList(
            grupper = grupper,
            adRolle = adRolle,
        )
    }

    private fun isRoleInUserGroupList(
        grupper: List<Gruppe>,
        adRolle: AdRolle,
    ): Boolean {
        return grupper.map { it.uuid }.contains(adRolle.id)
    }

    suspend fun getEnheterForVeileder(token: Token, callId: String): List<Enhet> {
        return getGrupperForVeilederOgCache(token, callId)
            .mapNotNull { it.getEnhetNr() }
            .map { Enhet(it) }
    }

    suspend fun getGrupperForVeilederOgCache(token: Token, callId: String): List<Gruppe> {
        val veilederIdent = token.getNAVIdent()
        val cacheKey = cacheKeyVeilederGrupper(veilederIdent)
        val cachedGrupper: List<Gruppe>? = valkeyStore.getListObject(cacheKey)

        if (cachedGrupper != null) {
            COUNT_CALL_MS_GRAPH_API_USER_GROUPS_PERSON_CACHE_HIT.increment()
            return cachedGrupper
        }

        COUNT_CALL_MS_GRAPH_API_USER_GROUPS_PERSON_CACHE_MISS.increment()
        return getGrupperForVeileder(token, callId).also {
            val tilgangTilMinstEnEnhet = it.mapNotNull { gruppe -> gruppe.getEnhetNr() }.isNotEmpty()
            val harSyfoRolle = isRoleInUserGroupList(it, adRoller.SYFO)
            if (harSyfoRolle && tilgangTilMinstEnEnhet) {
                valkeyStore.setObject(
                    key = cacheKey,
                    value = it,
                    expireSeconds = TWELVE_HOURS_IN_SECS,
                )
            }

            if (harSyfoRolle && !tilgangTilMinstEnEnhet) {
                log.error("Veileder doesn't have access to any enheter, callId=$callId")
            }
        }
    }

    suspend fun getGrupperForVeileder(token: Token, callId: String): List<Gruppe> {
        return try {
            getGroupsForVeilederRequest(token, callId)
                .map { it.toGruppe() }
                .apply { COUNT_CALL_MS_GRAPH_API_USER_GROUPS_PERSON_SUCCESS.increment() }
        } catch (e: Exception) {
            COUNT_CALL_MS_GRAPH_API_USER_GROUPS_PERSON_FAIL.increment()
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

    private fun Group.toGruppe(): Gruppe {
        return Gruppe(
            uuid = this.id,
            adGruppenavn = this.displayName,
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

        return try {
            withTimeout(TIMEOUT_LIMIT_MS) {
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

                groups
            }
        } catch (e: TimeoutCancellationException) {
            log.error("Timeout while requesting groups for veileder from Microsoft Graph API, callId=$callId", e)
            throw e
        }
    }

    fun createGraphServiceClient(azureAdToken: AzureAdToken): GraphServiceClient {
        val scopes = "$baseUrl/.default"
        return GraphServiceClient(azureAdToken.toTokenCredential(), scopes)
    }

    companion object {
        private const val MS_GRAPH_API_CACHE_VEILEDER_GRUPPER_PREFIX = "msGraphapiVeilederGrupper-"
        private val log = LoggerFactory.getLogger(GraphApiClient::class.java)
        const val TWELVE_HOURS_IN_SECS = 12 * 60 * 60L
        const val TIMEOUT_LIMIT_MS = 1_000L
        fun cacheKeyVeilederGrupper(veilederIdent: String) = "$MS_GRAPH_API_CACHE_VEILEDER_GRUPPER_PREFIX$veilederIdent"
    }
}
