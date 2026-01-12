package no.nav.syfo.tilgang

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.auth.isMissingNAVIdent
import no.nav.syfo.application.exception.ForbiddenAccessSystemConsumer
import no.nav.syfo.client.azuread.PreAuthorizedApp
import no.nav.syfo.util.*

const val tilgangApiBasePath = "/api/tilgang"
const val enhetNr = "enhetNr"
private val preloadApiAuthorizedApps = listOf("syfooversiktsrv")

fun Route.registerTilgangApi(
    tilgangService: TilgangService,
    preAuthorizedApps: List<PreAuthorizedApp>,
) {
    route(tilgangApiBasePath) {
        get("/navident/syfo") {
            val callId = call.getCallId()
            val token = call.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to check syfo tilgang for veileder. No Authorization header supplied")
            if (token.isMissingNAVIdent()) {
                throw IllegalArgumentException("Failed to check syfo tilgang for veileder. No NAV ident in token")
            }

            val tilgang = tilgangService.checkTilgangToSyfo(
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

            val syfoTilgang = tilgangService.checkTilgangToSyfo(
                token = token,
                callId = callId,
            )

            val tilgang = if (syfoTilgang.erAvslatt) {
                syfoTilgang
            } else {
                tilgangService.checkTilgangToEnhet(
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
                ?: throw IllegalArgumentException("Failed to check tilgang to person for veileder. No Authorization header supplied")
            if (token.isMissingNAVIdent()) {
                throw IllegalArgumentException("Failed to check tilgang to person for veileder. No NAV ident in token")
            }
            val appName = call.getAppname(preAuthorizedApps)
                ?: throw IllegalArgumentException("Failed to check tilgang to person for veileder. No consumer clientId was found")

            if (!tilgangService.hasAccessToSYFO(callId = callId, token = token)) {
                return@get call.respond(
                    status = HttpStatusCode.Forbidden,
                    message = Tilgang(erGodkjent = false)
                )
            }

            val tilgang = tilgangService.checkTilgangToPerson(
                token = token,
                personident = requestedPersonIdent,
                callId = callId,
                appName = appName,
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

        get("/navident/person/papirsykmelding") {
            val callId = call.getCallId()
            val requestedPersonIdent = call.getPersonidentHeader()
                ?: throw IllegalArgumentException("Did not find a PersonIdent in request headers, papirsykmelding")
            val token = call.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to check tilgang to person for veileder. No Authorization header supplied, papirsykmelding")
            if (token.isMissingNAVIdent()) {
                throw IllegalArgumentException("Failed to check tilgang to person for veileder. No NAV ident in token, papirsykmelding")
            }
            val appName = call.getAppname(preAuthorizedApps)
                ?: throw IllegalArgumentException("Failed to check tilgang to person for veileder. No consumer clientId was found, papirsykmelding")

            if (!tilgangService.hasAccessToSYFO(callId = callId, token = token)) {
                return@get call.respond(
                    status = HttpStatusCode.Forbidden,
                    message = Tilgang(erGodkjent = false)
                )
            }

            val tilgang = tilgangService.checkTilgangToPersonWithPapirsykmelding(
                token = token,
                personident = requestedPersonIdent,
                callId = callId,
                appName = appName,
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

        post("/navident/brukere") {
            val callId = call.getCallId()
            val token = call.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to check tilgang to brukere for veileder. No Authorization header supplied")
            if (token.isMissingNAVIdent()) {
                throw IllegalArgumentException("Failed to check tilgang to brukere for veileder. No NAV ident in token")
            }
            call.getAppname(preAuthorizedApps)
                ?: throw IllegalArgumentException("Failed to check tilgang to brukere for veileder. No consumer clientId was found")

            val personidenter = call.receive<List<String>>()

            val personidenterVeilederHasAccessTo = tilgangService.filterIdenterByVeilederAccess(
                callId = callId,
                token = token,
                personidenter = personidenter,
            )

            call.respond(HttpStatusCode.OK, personidenterVeilederHasAccessTo)
        }

        post("/system/preloadbrukere") {
            val consumerClientId = this.call.getConsumerClientId()
                ?: throw IllegalArgumentException("Failed to preload: Token or consumer clientId was not found")
            val preloadApiAuthorizedClientIds = preAuthorizedApps
                .filter { preloadApiAuthorizedApps.contains(it.getAppnavn()) }
                .map { it.clientId }
            if (!preloadApiAuthorizedClientIds.contains(consumerClientId)) {
                throw ForbiddenAccessSystemConsumer(consumerClientIdAzp = consumerClientId)
            }

            val callId = call.getCallId()
            val personidenter = call.receive<List<String>>()

            tilgangService.preloadCacheForPersonAccess(
                callId = callId,
                personidenter = personidenter,
            )

            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun ApplicationCall.getAppname(
    preAuthorizedApps: List<PreAuthorizedApp>,
): String? {
    val consumerClientId = this.getConsumerClientId() ?: ""
    return preAuthorizedApps.find { it.clientId == consumerClientId }?.getAppnavn()
}
