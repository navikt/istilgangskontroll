package no.nav.syfo.util

import net.logstash.logback.argument.StructuredArguments

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"
const val NAV_PERSONIDENT_HEADER = "nav-personident"
const val NAV_CONSUMER_ID_HEADER = "Nav-Consumer-Id"
const val NAV_CONSUMER_APP_ID = "istilgangskontroll"

const val TEMA_HEADER = "Tema"
const val ALLE_TEMA_HEADERVERDI = "GEN"

fun bearerHeader(token: String) = "Bearer $token"
fun callIdArgument(callId: String) = StructuredArguments.keyValue("callId", callId)!!
