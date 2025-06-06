package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonNull.content
import no.nav.syfo.testhelper.UserConstants

suspend fun MockRequestHandleScope.getTilgangsmaskinResponse(request: HttpRequestData): HttpResponseData {
    val personident = request.receiveBody<String>()

    return when (personident) {
        UserConstants.PERSONIDENT_SKJERMET -> {
            respond(
                content = "",
                status = HttpStatusCode.Forbidden,
            )
        }
        else -> {
            respond(
                content = "",
                status = HttpStatusCode.NoContent
            )
        }
    }
}
