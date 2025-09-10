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

                requestUrl.startsWith("/${env.clients.skjermedePersoner.baseUrl}") -> {
                    getSkjermedePersonerResponse(request)
                }

                requestUrl.startsWith("/${env.clients.pdl.baseUrl}") -> {
                    getPdlResponse(request)
                }

                requestUrl.startsWith("/${env.clients.tilgangsmaskin.baseUrl}") -> {
                    getTilgangsmaskinResponse(request)
                }

                requestUrl.startsWith("/${env.clients.behandlendeEnhet.baseUrl}") -> {
                    getBehandlendeEnhetResponse(request)
                }

                requestUrl.startsWith("/${env.clients.norgUrl}/norg2/api/v1/enhet/navkontor") -> {
                    getNorgGeografiskEnhetResponse(request)
                }

                requestUrl.startsWith("/${env.clients.norgUrl}") -> {
                    getNorgOverordnedeEnheterResponse(request)
                }

                else -> {
                    error("Unhandled ${request.url.encodedPath}")
                }
            }
        }
    }
}
