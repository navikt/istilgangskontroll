package no.nav.syfo.util

import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import net.logstash.logback.argument.StructuredArguments

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"
const val NAV_PERSONIDENT_HEADER = "nav-personident"

fun bearerHeader(token: String) = "Bearer $token"
fun callIdArgument(callId: String) = StructuredArguments.keyValue("callId", callId)!!
