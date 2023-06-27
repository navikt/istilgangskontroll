package no.nav.syfo.tilgang

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.util.getBearerHeader
import no.nav.syfo.util.getCallId

const val tilgangApiBasePath = "/api/tilgang"

fun Route.registerTilgangApi(
    tilgangService: TilgangService,
) {
    route(tilgangApiBasePath) {
        get("/navident/syfo") {
            val token = call.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to check syfo tilgang for veileder. No Authorization header supplied")
            val callId = call.getCallId()

            val tilgang = tilgangService.sjekkTilgangTilTjenesten(
                token = token,
                callId = callId,
            )

            if (tilgang.harTilgang) {
                call.respond(tilgang)
            } else {
                call.respond(
                    status = HttpStatusCode.Forbidden,
                    message = tilgang
                )
            }
        }
    }
}
