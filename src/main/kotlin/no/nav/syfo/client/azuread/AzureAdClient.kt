package no.nav.syfo.client.azuread

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.client.httpClientProxy
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class AzureAdClient(
    private val azureEnvironment: AzureEnvironment,
    private val httpClient: HttpClient = httpClientProxy()
) {
    suspend fun getOnBehalfOfToken(scopeClientId: String, token: String, callId: String): AzureAdToken? = getAccessToken(
        formParameters = buildParameters(token, "api://$scopeClientId/.default"),
        callId = callId,
    )?.toAzureAdToken()

    suspend fun getOnBehalfOfTokenForGraphApi(scopeClientId: String, token: String, callId: String): AzureAdToken? = getAccessToken(
        formParameters = buildParameters(token, "$scopeClientId/.default"),
        callId = callId,
    )?.toAzureAdToken()

    private fun buildParameters(token: String, scope: String) = Parameters.build {
        append("client_id", azureEnvironment.appClientId)
        append("client_secret", azureEnvironment.appClientSecret)
        append("client_assertion_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
        append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
        append("assertion", token)
        append("scope", scope)
        append("requested_token_use", "on_behalf_of")
    }

    suspend fun getSystemToken(scopeClientId: String, callId: String): AzureAdToken? {
        val cacheKey = "$CACHE_AZUREAD_TOKEN_SYSTEM_KEY_PREFIX$scopeClientId"
        val cachedToken = cache.get(key = cacheKey)
        return if (cachedToken?.isExpired() == false) {
            cachedToken
        } else {
            val azureAdTokenResponse = getAccessToken(
                formParameters = Parameters.build {
                    append("client_id", azureEnvironment.appClientId)
                    append("client_secret", azureEnvironment.appClientSecret)
                    append("grant_type", "client_credentials")
                    append("scope", "api://$scopeClientId/.default")
                },
                callId = callId,
            )
            azureAdTokenResponse?.let { token ->
                token.toAzureAdToken().also {
                    cache[cacheKey] = it
                }
            }
        }
    }

    private suspend fun getAccessToken(
        formParameters: Parameters,
        callId: String,
    ): AzureAdTokenResponse? =
        try {
            val response: HttpResponse = httpClient.post(azureEnvironment.openidConfigTokenEndpoint) {
                accept(ContentType.Application.Json)
                setBody(FormDataContent(formParameters))
            }
            response.body<AzureAdTokenResponse>()
        } catch (e: ResponseException) {
            handleUnexpectedResponseException(e, callId)
            null
        }

    private fun handleUnexpectedResponseException(
        responseException: ResponseException,
        callId: String,
    ) {
        log.error(
            "Error while requesting AzureAdAccessToken with statusCode=${responseException.response.status.value}, callId: $callId",
            responseException
        )
    }

    companion object {
        const val CACHE_AZUREAD_TOKEN_SYSTEM_KEY_PREFIX = "azuread-token-system-"
        private val cache = ConcurrentHashMap<String, AzureAdToken>()
        private val log = LoggerFactory.getLogger(AzureAdClient::class.java)
    }
}
