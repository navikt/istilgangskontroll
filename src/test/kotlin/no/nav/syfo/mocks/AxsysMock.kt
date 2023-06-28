package no.nav.syfo.mocks

import com.auth0.jwt.JWT
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.application.api.auth.JWT_CLAIM_NAVIDENT
import no.nav.syfo.client.axsys.AxsysEnhet
import no.nav.syfo.testhelper.UserConstants

private val axsysEnhetResponse = AxsysEnhet(
    enhetId = UserConstants.VEILEDER_ENHET,
    navn = "enhet",
)

fun MockRequestHandleScope.getAxsysResponse(request: HttpRequestData): HttpResponseData {
    val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
    val veilederIdent = JWT.decode(token).claims[JWT_CLAIM_NAVIDENT]?.asString()

    return when (veilederIdent) {
        UserConstants.VEILEDER_IDENT -> {
            respond(
                content = mapper.writeValueAsString(listOf(axsysEnhetResponse)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        else -> respondBadRequest()
    }
}
