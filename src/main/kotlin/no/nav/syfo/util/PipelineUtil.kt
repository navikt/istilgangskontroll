package no.nav.syfo.util

import com.auth0.jwt.JWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.pipeline.*

const val JWT_CLAIM_AZP = "azp"
const val JWT_CLAIM_NAVIDENT = "NAVident"

fun ApplicationCall.getCallId(): String = this.request.headers[NAV_CALL_ID_HEADER].toString()

fun ApplicationCall.getConsumerClientId(): String? =
    getBearerHeader()?.let {
        JWT.decode(it).claims[JWT_CLAIM_AZP]?.asString()
    }

fun ApplicationCall.getBearerHeader(): String? =
    this.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")

fun PipelineContext<out Unit, ApplicationCall>.getCallId(): String {
    return this.call.getCallId()
}
