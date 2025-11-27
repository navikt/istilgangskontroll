package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.tilgangsmaskin.AvvisningsKode
import no.nav.syfo.client.tilgangsmaskin.ProblemDetailResponse
import no.nav.syfo.client.tilgangsmaskin.TilgangsmaskinBulkResponse
import no.nav.syfo.client.tilgangsmaskin.TilgangsmaskinBulkResultat
import no.nav.syfo.testhelper.UserConstants
import kotlin.String

private val tilgangsmaskinPath: String = "/api/v1/komplett"
private val tilgangsmaskinBulkPath: String = "/api/v1/bulk/obo"

suspend fun MockRequestHandleScope.getTilgangsmaskinResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath

    return if (requestUrl.contains(tilgangsmaskinPath)) {
        val personident = request.receiveBody<String>()

        when (personident) {
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
    } else if (requestUrl.contains(tilgangsmaskinBulkPath)) {
        val personidenter = request.receiveBody<List<String>>()
        respond(
            content = mapper.writeValueAsString(
                TilgangsmaskinBulkResponse(
                    resultater = personidenter.map {
                        TilgangsmaskinBulkResultat(
                            brukerId = it,
                            status = if (it == UserConstants.PERSONIDENT_SKJERMET) {
                                HttpStatusCode.Forbidden.value
                            } else {
                                HttpStatusCode.NoContent.value
                            }
                        )
                    }
                )
            ),
            status = HttpStatusCode.MultiStatus
        )
    } else {
        throw RuntimeException("Unknown path: $requestUrl")
    }
}
