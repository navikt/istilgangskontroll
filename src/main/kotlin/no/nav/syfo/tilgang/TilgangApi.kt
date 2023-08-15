package no.nav.syfo.tilgang

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.auth.isMissingNAVIdent
import no.nav.syfo.util.*

const val tilgangApiBasePath = "/api/tilgang"
const val enhetNr = "enhetNr"

fun Route.registerTilgangApi(
    tilgangService: TilgangService,
) {
    route(tilgangApiBasePath) {
        get("/navident/syfo") {
            val callId = call.getCallId()
            val token = call.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to check syfo tilgang for veileder. No Authorization header supplied")
            if (token.isMissingNAVIdent()) {
                throw IllegalArgumentException("Failed to check enhetstilgang for veileder. No NAV ident in token")
            }

            val tilgang = tilgangService.hasTilgangToSyfo(
                token = token,
                callId = callId,
            )

            if (tilgang.erGodkjent) {
                call.respond(tilgang)
            } else {
                call.respond(
                    status = HttpStatusCode.Forbidden,
                    message = tilgang
                )
            }
        }

        get("/navident/enhet/{$enhetNr}") {
            val callId = call.getCallId()
            val token = call.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to check enhetstilgang for veileder. No Authorization header supplied")
            if (token.isMissingNAVIdent()) {
                throw IllegalArgumentException("Failed to check enhetstilgang for veileder. No NAV ident in token")
            }

            val enhetNr = call.parameters[enhetNr] ?: throw IllegalArgumentException("No EnhetNr found in path param")
            val enhet = Enhet(enhetNr)

            val syfoTilgang = tilgangService.hasTilgangToSyfo(
                token = token,
                callId = callId,
            )

            val tilgang = if (syfoTilgang.erAvslatt) {
                syfoTilgang
            } else {
                tilgangService.hasTilgangToEnhet(
                    token = token,
                    callId = callId,
                    enhet = enhet,
                )
            }

            if (tilgang.erGodkjent) {
                call.respond(tilgang)
            } else {
                call.respond(
                    status = HttpStatusCode.Forbidden,
                    message = tilgang
                )
            }
        }

        get("/navident/person") {
            val callId = call.getCallId()
            val requestedPersonIdent = call.getPersonidentHeader()
                ?: throw IllegalArgumentException("Did not find a PersonIdent in request headers")
            val token = call.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to check syfo tilgang for veileder. No Authorization header supplied")
            if (token.isMissingNAVIdent()) {
                throw IllegalArgumentException("Failed to check enhetstilgang for veileder. No NAV ident in token")
            }

            val tilgang = tilgangService.hasTilgangToPerson(
                token = token,
                personident = requestedPersonIdent,
                callId = callId,
            )

            // TODO: Add auditlog
            if (tilgang.erGodkjent) {
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
