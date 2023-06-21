package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.mocks.getMockHttpClient
import no.nav.syfo.tilgang.AdRoller

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
) {
    val adRoller = AdRoller(env = externalMockEnvironment.environment)

    val mockHttpClient = getMockHttpClient(env = externalMockEnvironment.environment)

    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        httpClient = mockHttpClient,
    )

    val graphApiClient = GraphApiClient(
        azureAdClient = azureAdClient,
        baseUrl = externalMockEnvironment.environment.clients.graphApiUrl,
        relevantSyfoRoller = adRoller.toList(),
        httpClient = mockHttpClient,
    )

    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        environment = externalMockEnvironment.environment,
        graphApiClient = graphApiClient,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        adRoller = adRoller,
    )
}
