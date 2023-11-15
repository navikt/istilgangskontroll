package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.pdl.*
import no.nav.syfo.testhelper.UserConstants

private val PdlResponse = PipPersondataResponse(
    person = PipPerson(
        adressebeskyttelse = listOf(
            PipAdressebeskyttelse(
                gradering = Gradering.UGRADERT
            )
        ),
        doedsfall = emptyList(),
    ),
    geografiskTilknytning = PipGeografiskTilknytning(
        gtType = PdlGeografiskTilknytningType.KOMMUNE.name,
        gtBydel = null,
        gtKommune = "0301",
        gtLand = null,
    ),
    identer = PipIdenter(emptyList()),
)

private val gradertPdlResponse = PipPersondataResponse(
    person = PipPerson(
        adressebeskyttelse = listOf(
            PipAdressebeskyttelse(
                gradering = Gradering.STRENGT_FORTROLIG,
            )
        ),
        doedsfall = emptyList(),
    ),
    geografiskTilknytning = PipGeografiskTilknytning(
        gtType = PdlGeografiskTilknytningType.KOMMUNE.name,
        gtBydel = null,
        gtKommune = "0301",
        gtLand = null,
    ),
    identer = PipIdenter(emptyList()),
)

fun MockRequestHandleScope.getPdlResponse(request: HttpRequestData): HttpResponseData {
    val personident = request.headers[PdlClient.IDENT_HEADER]

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
