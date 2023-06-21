package no.nav.syfo.tilgang

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.util.getBearerHeader

const val tilgangApiBasePath = "/api/tilgang"

fun Route.registerTilgangApi(
    tilgangService: TilgangService,
) {
    route(tilgangApiBasePath) {
        get("/navident/syfo") {
            val token = call.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to check syfo tilgang for veileder. No Authorization header supplied")
            val tilgang = tilgangService.sjekkTilgangTilTjenesten(token)

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
