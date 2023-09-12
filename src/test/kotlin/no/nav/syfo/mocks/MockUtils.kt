package no.nav.syfo.mocks

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.util.configuredJacksonMapper

val mapper = configuredJacksonMapper()

suspend inline fun <reified T> HttpRequestData.receiveBody(): T {
    return mapper.readValue(body.toByteArray(), T::class.java)
}
