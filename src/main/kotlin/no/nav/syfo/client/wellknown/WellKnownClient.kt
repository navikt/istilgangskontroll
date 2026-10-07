package no.nav.syfo.client.wellknown

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.httpClientProxy

private val httpClient = httpClientProxy()

fun getWellKnown(
    wellKnownUrl: String,
): WellKnown = runBlocking {
    httpClient.get(wellKnownUrl).body<WellKnownDTO>().toWellKnown()
}
