package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.pdl.*

val response = PdlPersonResponse(
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

fun MockRequestHandleScope.getPdlResponse(request: HttpRequestData): HttpResponseData {
    return respond(
        content = mapper.writeValueAsString(response),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}