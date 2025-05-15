package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetDTO
import no.nav.syfo.client.behandlendeenhet.EnhetDTO
import no.nav.syfo.testhelper.UserConstants

private val behandlendeEnhetResponse = BehandlendeEnhetDTO(
    geografiskEnhet = EnhetDTO(
        enhetId = UserConstants.ENHET_VEILEDER,
        navn = "enhet",
    ),
    oppfolgingsenhetDTO = null,
)

fun MockRequestHandleScope.getBehandlendeEnhetResponse(request: HttpRequestData): HttpResponseData {
    return respond(
        content = mapper.writeValueAsString(behandlendeEnhetResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}
