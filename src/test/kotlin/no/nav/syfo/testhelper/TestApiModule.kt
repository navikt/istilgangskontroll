package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.tilgangsmaskin.TilgangsmaskinClient
import no.nav.syfo.mocks.getMockHttpClient
import no.nav.syfo.tilgang.AdRoller

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    graphApiClientMock: GraphApiClient? = null,
) {
    val adRoller = AdRoller(env = externalMockEnvironment.environment)

    val mockHttpClient = getMockHttpClient(env = externalMockEnvironment.environment)

    val valkeyStore = externalMockEnvironment.valkeyStore

    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        valkeyStore = valkeyStore,
        httpClient = mockHttpClient,
    )

    val graphApiClient = graphApiClientMock ?: GraphApiClient(
        azureAdClient = azureAdClient,
        baseUrl = externalMockEnvironment.environment.clients.graphApiUrl,
        valkeyStore = valkeyStore,
        adRoller = adRoller,
    )

    val tilgangsmaskinClient = TilgangsmaskinClient(
        azureAdClient = azureAdClient,
        baseUrl = externalMockEnvironment.environment.clients.tilgangsmaskin.baseUrl,
        clientId = externalMockEnvironment.environment.clients.tilgangsmaskin.clientId,
        httpClient = mockHttpClient,
    )

    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        environment = externalMockEnvironment.environment,
        graphApiClient = graphApiClient,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        adRoller = adRoller,
        valkeyStore = valkeyStore,
        tilgangsmaskin = tilgangsmaskinClient,
    )
}
