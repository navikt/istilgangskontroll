package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*

fun MockRequestHandleScope.getSkjermedePersonerResponse(request: HttpRequestData): HttpResponseData {
    return respond(
        content = mapper.writeValueAsString(false),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}
