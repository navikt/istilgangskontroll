package no.nav.syfo.util

import com.auth0.jwt.JWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.domain.Personident

const val JWT_CLAIM_AZP = "azp"

fun ApplicationCall.getCallId(): String = this.request.headers[NAV_CALL_ID_HEADER].toString()

fun ApplicationCall.getConsumerClientId(): String? =
    getBearerHeader()?.let {
        JWT.decode(it.value).claims[JWT_CLAIM_AZP]?.asString()
    }

fun ApplicationCall.getBearerHeader(): Token? =
    this.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.let { Token(it) }

fun RoutingContext.getCallId(): String {
    return this.call.getCallId()
}

fun ApplicationCall.getPersonidentHeader(): Personident? =
    this.request.headers[NAV_PERSONIDENT_HEADER]?.let { Personident(it) }
