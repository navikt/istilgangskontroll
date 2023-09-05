package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetDTO
import no.nav.syfo.testhelper.UserConstants

private val behandlendeEnhetResponse = BehandlendeEnhetDTO(
    enhetId = UserConstants.VEILEDER_ENHET,
    navn = "enhet",
)

fun MockRequestHandleScope.getBehandlendeEnhetResponse(request: HttpRequestData): HttpResponseData {
    return respond(
        content = mapper.writeValueAsString(behandlendeEnhetResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}
