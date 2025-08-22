package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.tilgangsmaskin.AvvisningsKode
import no.nav.syfo.client.tilgangsmaskin.ProblemDetailResponse
import no.nav.syfo.testhelper.UserConstants
import kotlin.String

suspend fun MockRequestHandleScope.getTilgangsmaskinResponse(request: HttpRequestData): HttpResponseData {
    val personident = request.receiveBody<String>()

    return when (personident) {
        UserConstants.PERSONIDENT_SKJERMET -> {
            respond(
                content = mapper.writeValueAsString(
                    ProblemDetailResponse(
                        title = AvvisningsKode.AVVIST_SKJERMING,
                        status = 403,
                        instance = "",
                        brukerIdent = personident,
                        navIdent = UserConstants.VEILEDER_IDENT,
                        begrunnelse = "Bruker er skjermet",
                        traceId = "traceId",
                        kanOverstyres = false,
                    )
                ),
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
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
