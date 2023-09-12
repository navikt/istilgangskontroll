package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.skjermedepersoner.SkjermedePersonerRequestDTO
import no.nav.syfo.testhelper.UserConstants

suspend fun MockRequestHandleScope.getSkjermedePersonerResponse(request: HttpRequestData): HttpResponseData {
    val skjermedePersonerRequestDTO = request.receiveBody<SkjermedePersonerRequestDTO>()
    val personident = skjermedePersonerRequestDTO.personident

    return when (personident) {
        UserConstants.PERSONIDENT_SKJERMET -> {
            respond(
                content = mapper.writeValueAsString(true),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        else -> {
            respond(
                content = mapper.writeValueAsString(false),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    }
}
