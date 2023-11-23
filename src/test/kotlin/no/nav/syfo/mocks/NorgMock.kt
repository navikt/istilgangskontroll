package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.norg.domain.NorgEnhet
import no.nav.syfo.testhelper.UserConstants

private val overordnetNorgEnhet = NorgEnhet(
    enhetNr = UserConstants.ENHET_OVERORDNET,
    navn = "enhet",
    status = "aktiv",
    aktiveringsdato = null,
    antallRessurser = null,
    kanalstrategi = null,
    nedleggelsesdato = null,
    oppgavebehandler = null,
    orgNivaa = null,
    orgNrTilKommunaltNavKontor = null,
    organisasjonsnummer = null,
    sosialeTjenester = null,
    type = null,
    underAvviklingDato = null,
    underEtableringDato = null,
    versjon = null,
)

private val overordnetNorgResponse = listOf(overordnetNorgEnhet)

fun MockRequestHandleScope.getNorgOverordnedeEnheterResponse(request: HttpRequestData): HttpResponseData {
    val url = request.url.encodedPath
    return if (url.contains(UserConstants.ENHET_OVERORDNET_NOT_FOUND)) {
        respondError(status = HttpStatusCode.NotFound)
    } else {
        respond(
            content = mapper.writeValueAsString(overordnetNorgResponse),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
}

val geografiskNorgEnhet = overordnetNorgEnhet.copy(
    enhetNr = UserConstants.ENHET_VEILEDER,
)

fun MockRequestHandleScope.getNorgGeografiskEnhetResponse(request: HttpRequestData): HttpResponseData {
    return respond(
        content = mapper.writeValueAsString(geografiskNorgEnhet),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}
