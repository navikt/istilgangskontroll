package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.api.auth.getNAVIdent
import no.nav.syfo.client.azuread.AzureAdTokenResponse
import no.nav.syfo.testhelper.UserConstants

private fun azureAdTokenResponse(token: String?) = AzureAdTokenResponse(
    access_token = token ?: "token",
    expires_in = 3600,
    token_type = "type",
)

fun MockRequestHandleScope.getAzureAdResponse(request: HttpRequestData): HttpResponseData {
    val token = (request.body as FormDataContent).formData["assertion"]
    val veilederIdent: String? = if (token != null) Token(token).getNAVIdent() else null
    return when (veilederIdent) {
        UserConstants.VEILEDER_IDENT_NO_AZURE_AD_TOKEN -> respond(
            content = "No token here!",
            status = HttpStatusCode.NotFound,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
        else -> respond(
            content = mapper.writeValueAsString(azureAdTokenResponse(token)),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
}
