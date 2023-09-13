package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.norg.domain.NorgEnhet
import no.nav.syfo.testhelper.UserConstants

private val norgEnhet = NorgEnhet(
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

private val norgResponse = listOf(norgEnhet)

fun MockRequestHandleScope.getNorgResponse(request: HttpRequestData): HttpResponseData {
    return respond(
        content = mapper.writeValueAsString(norgResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}
