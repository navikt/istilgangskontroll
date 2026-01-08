package no.nav.syfo.client.azuread

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.api.auth.getNAVIdent
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.client.httpClientProxy
import org.slf4j.LoggerFactory

class AzureAdClient(
    private val azureEnvironment: AzureEnvironment,
    private val valkeyStore: ValkeyStore,
    private val httpClient: HttpClient = httpClientProxy()
) {
    suspend fun getOnBehalfOfToken(scopeClientId: String, token: Token, callId: String): AzureAdToken? =
        getOnBehalfOfToken(
            scopeClientId = scopeClientId,
            token = token,
            callId = callId,
            formParameters = buildParameters(token, "api://$scopeClientId/.default"),
        )

    suspend fun getOnBehalfOfTokenForGraphApi(scopeClientId: String, token: Token, callId: String): AzureAdToken? =
        getOnBehalfOfToken(
            scopeClientId = scopeClientId,
            token = token,
            callId = callId,
            formParameters = buildParameters(token, "$scopeClientId/.default"),
        )

    private suspend fun getOnBehalfOfToken(
        scopeClientId: String,
        token: Token,
        callId: String,
        formParameters: Parameters,
    ): AzureAdToken? {
        val veilederIdent = token.getNAVIdent()
        val cacheKey = "$CACHE_AZUREAD_TOKEN_OBO_KEY_PREFIX$scopeClientId-$veilederIdent"
        val cachedOboToken: AzureAdToken? = valkeyStore.getObject(key = cacheKey)
        return if (cachedOboToken?.isExpired() == false) {
            COUNT_AZURE_AD_CACHE_HIT.increment()
            cachedOboToken
        } else {
            COUNT_AZURE_AD_CACHE_MISS.increment()
            val azureAdTokenResponse = getAccessToken(
                formParameters = formParameters,
                callId = callId,
            )

            azureAdTokenResponse?.toAzureAdToken()?.also { oboToken ->
                valkeyStore.setObject(
                    key = cacheKey,
                    value = oboToken,
                    expireSeconds = azureAdTokenResponse.expires_in,
                )
            }
        }
    }

    private fun buildParameters(token: Token, scope: String) = Parameters.build {
        append("client_id", azureEnvironment.appClientId)
        append("client_secret", azureEnvironment.appClientSecret)
        append("client_assertion_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
        append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
        append("assertion", token.value)
        append("scope", scope)
        append("requested_token_use", "on_behalf_of")
    }

    suspend fun getSystemToken(scopeClientId: String, callId: String): AzureAdToken? {
        val cacheKey = "$CACHE_AZUREAD_TOKEN_SYSTEM_KEY_PREFIX$scopeClientId"
        val cachedSystemToken: AzureAdToken? = valkeyStore.getObject(key = cacheKey)
        return if (cachedSystemToken?.isExpired() == false) {
            COUNT_AZURE_AD_CACHE_HIT.increment()
            cachedSystemToken
        } else {
            COUNT_AZURE_AD_CACHE_MISS.increment()
            val azureAdTokenResponse = getAccessToken(
                formParameters = Parameters.build {
                    append("client_id", azureEnvironment.appClientId)
                    append("client_secret", azureEnvironment.appClientSecret)
                    append("grant_type", "client_credentials")
                    append("scope", "api://$scopeClientId/.default")
                },
                callId = callId,
            )

            azureAdTokenResponse?.toAzureAdToken()?.also { oboToken ->
                valkeyStore.setObject(
                    key = cacheKey,
                    value = oboToken,
                    expireSeconds = azureAdTokenResponse.expires_in,
                )
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
        const val CACHE_AZUREAD_TOKEN_OBO_KEY_PREFIX = "azuread-token-obo-"
        private val log = LoggerFactory.getLogger(AzureAdClient::class.java)
    }
}
