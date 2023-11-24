package no.nav.syfo.mocks

import com.auth0.jwt.JWT
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.application.api.auth.JWT_CLAIM_NAVIDENT
import no.nav.syfo.client.graphapi.GraphApiGroup
import no.nav.syfo.client.graphapi.GraphApiUserGroupsResponse
import no.nav.syfo.testhelper.UserConstants

private val coffeAccess = GraphApiGroup(id = "123", displayName = "Coffe drinking", mailNickname = "XYZ_coffedrinking")
private val syfoAccess = GraphApiGroup(id = "syfoId", displayName = "SYFO", mailNickname = "0000-GA-SYFO-SENSITIV")
private val papirsykmeldingAccess = GraphApiGroup(id = "papirsykmeldingId", displayName = "PAPIRSYKMELDING", mailNickname = "0000-GA-papirsykmelding")
private val responseWithAccess = GraphApiUserGroupsResponse(value = listOf(coffeAccess, syfoAccess, papirsykmeldingAccess))
private val responseNoAccess = GraphApiUserGroupsResponse(value = listOf(coffeAccess))

fun MockRequestHandleScope.getGraphApiResponse(request: HttpRequestData): HttpResponseData {
    val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
    val veilederIdent = JWT.decode(token).claims[JWT_CLAIM_NAVIDENT]?.asString()

    return when (veilederIdent) {
        UserConstants.VEILEDER_IDENT_NO_SYFO_ACCESS, UserConstants.VEILEDER_IDENT_NO_PAPIRSYKMELDING_ACCESS -> {
            respond(
                content = mapper.writeValueAsString(responseNoAccess),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        else -> {
            respond(
                content = mapper.writeValueAsString(responseWithAccess),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    }
}
