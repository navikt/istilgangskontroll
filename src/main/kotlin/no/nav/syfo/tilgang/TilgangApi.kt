package no.nav.syfo.tilgang

import com.auth0.jwt.JWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.auth.getNAVIdent
import no.nav.syfo.application.api.auth.isMissingNAVIdent
import no.nav.syfo.application.exception.ForbiddenAccessSystemConsumer
import no.nav.syfo.audit.*
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
                throw IllegalArgumentException("Failed to check enhetstilgang for veileder. No NAV ident in token")
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
                ?: throw IllegalArgumentException("Failed to check syfo tilgang for veileder. No Authorization header supplied")
            if (token.isMissingNAVIdent()) {
                throw IllegalArgumentException("Failed to check enhetstilgang for veileder. No NAV ident in token")
            }
            val veilederIdent = token.getNAVIdent()
            val consumerClientId = call.getConsumerClientId() ?: ""

            val tilgang = tilgangService.checkTilgangToPerson(
                token = token,
                personident = requestedPersonIdent,
                callId = callId,
            )

            auditLog(
                CEF(
                    suid = veilederIdent,
                    duid = requestedPersonIdent.value,
                    event = AuditLogEvent.Access,
                    permit = tilgang.erGodkjent,
                    appName = consumerClientId, // TODO: Get app name instead of client id
                )
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
                ?: throw IllegalArgumentException("Failed to check syfo tilgang for veileder. No Authorization header supplied")
            if (token.isMissingNAVIdent()) {
                throw IllegalArgumentException("Failed to check enhetstilgang for veileder. No NAV ident in token")
            }
            val personidenter = call.receive<List<String>>()

            val personidenterVeilederHasAccessTo = tilgangService.filterIdenterByVeilederAccess(
                callId = callId,
                token = token,
                personidenter = personidenter,
            )

            call.respond(HttpStatusCode.OK, personidenterVeilederHasAccessTo)
        }

        post("/system/preloadbrukere") {
            val token = this.call.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to preload: No token supplied in request header")
            val consumerClientIdAzp: String = JWT.decode(token.value).claims[JWT_CLAIM_AZP]?.asString()
                ?: throw IllegalArgumentException("Claim AZP was not found in token")
            val preloadApiAuthorizedClientIds = preAuthorizedApps
                .filter { preloadApiAuthorizedApps.contains(it.getAppnavn()) }
                .map { it.clientId }
            if (!preloadApiAuthorizedClientIds.contains(consumerClientIdAzp)) {
                throw ForbiddenAccessSystemConsumer(consumerClientIdAzp = consumerClientIdAzp)
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
