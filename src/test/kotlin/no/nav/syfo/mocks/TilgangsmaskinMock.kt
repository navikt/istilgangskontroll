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
        val avvisningsKode = personident.tilAvvisningsKode()

        if (avvisningsKode != null) {
            respond(
                content = mapper.writeValueAsString(
                    ProblemDetailResponse(
                        title = avvisningsKode,
                        status = 403,
                        instance = "",
                        brukerIdent = personident,
                        navIdent = UserConstants.VEILEDER_IDENT,
                        begrunnelse = "Avvist av tilgangsmaskin",
                        traceId = "traceId",
                        kanOverstyres = false,
                    )
                ),
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        } else {
            respond(
                content = "",
                status = HttpStatusCode.NoContent
            )
        }
    } else if (requestUrl.contains(tilgangsmaskinBulkPath)) {
        val personidenter = request.receiveBody<List<String>>()
        respond(
            content = mapper.writeValueAsString(
                TilgangsmaskinBulkResponse(
                    resultater = personidenter.map {
                        TilgangsmaskinBulkResultat(
                            brukerId = it,
                            status = if (it.tilAvvisningsKode() != null) {
                                HttpStatusCode.Forbidden.value
                            } else {
                                HttpStatusCode.NoContent.value
                            }
                        )
                    }
                )
            ),
            status = HttpStatusCode.MultiStatus,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    } else {
        throw RuntimeException("Unknown path: $requestUrl")
    }
}

private fun String.tilAvvisningsKode(): AvvisningsKode? = when (this) {
    UserConstants.PERSONIDENT_SKJERMET -> AvvisningsKode.AVVIST_SKJERMING
    UserConstants.PERSONIDENT_GRADERT -> AvvisningsKode.AVVIST_STRENGT_FORTROLIG_ADRESSE
    UserConstants.PERSONIDENT_VERGE -> AvvisningsKode.AVVIST_VERGEMÅL
    UserConstants.PERSONIDENT_OTHER_ENHET -> AvvisningsKode.AVVIST_GEOGRAFISK
    else -> null
}
