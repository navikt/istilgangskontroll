package no.nav.syfo.mocks

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import no.nav.syfo.application.Environment
import no.nav.syfo.client.commonConfig

fun getMockHttpClient(env: Environment) = HttpClient(MockEngine) {
    commonConfig()
    engine {
        addHandler { request ->
            val requestUrl = request.url.encodedPath
            when {
                requestUrl == "/${env.azure.openidConfigTokenEndpoint}" -> {
                    getAzureAdResponse(request)
                }

                requestUrl.startsWith("/${env.clients.graphApiUrl}") -> {
                    getGraphApiResponse(request)
                }

                requestUrl.startsWith("/${env.clients.axsys.baseUrl}") -> {
                    getAxsysResponse(request)
                }

                requestUrl.startsWith("/${env.clients.skjermedePersoner.baseUrl}") -> {
                    getSkjermedePersonerResponse(request)
                }

                else -> {
                    error("Unhandled ${request.url.encodedPath}")
                }
            }
        }
    }
}
