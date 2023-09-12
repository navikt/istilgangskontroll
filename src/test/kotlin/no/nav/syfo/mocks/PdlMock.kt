package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.pdl.*
import no.nav.syfo.testhelper.UserConstants

private val PdlResponse = PdlPersonResponse(
    errors = emptyList(),
    data = PdlHentPerson(
        hentPerson = PdlPerson(
            adressebeskyttelse = listOf(
                Adressebeskyttelse(
                    gradering = Gradering.UGRADERT
                )
            )
        )
    )
)

private val gradertPdlResponse = PdlPersonResponse(
    errors = emptyList(),
    data = PdlHentPerson(
        hentPerson = PdlPerson(
            adressebeskyttelse = listOf(
                Adressebeskyttelse(
                    gradering = Gradering.STRENGT_FORTROLIG
                )
            )
        )
    )
)

suspend fun MockRequestHandleScope.getPdlResponse(request: HttpRequestData): HttpResponseData {
    val pdlRequest = request.receiveBody<PdlRequest>()
    val personident = pdlRequest.variables.ident

    return when (personident) {
        UserConstants.PERSONIDENT_GRADERT -> {
            respond(
                content = mapper.writeValueAsString(gradertPdlResponse),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        else -> {
            respond(
                content = mapper.writeValueAsString(PdlResponse),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    }
}
