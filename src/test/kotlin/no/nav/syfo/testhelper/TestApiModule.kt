package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.axsys.AxsysClient
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.skjermedepersoner.SkjermedePersonerPipClient
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

    val axsysClient = AxsysClient(
        azureAdClient = azureAdClient,
        axsysUrl = externalMockEnvironment.environment.clients.axsys.baseUrl,
        clientId = externalMockEnvironment.environment.clients.axsys.clientId,
        httpClient = mockHttpClient,
    )

    val skjermedePersonerPipClient = SkjermedePersonerPipClient(
        azureAdClient = azureAdClient,
        skjermedePersonerUrl = externalMockEnvironment.environment.clients.skjermedePersoner.baseUrl,
        clientId = externalMockEnvironment.environment.clients.skjermedePersoner.clientId,
        httpClient = mockHttpClient,
    )

    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        baseUrl = externalMockEnvironment.environment.clients.pdl.baseUrl,
        clientId = externalMockEnvironment.environment.clients.pdl.clientId,
        httpClient = mockHttpClient,
    )

    val behandlendeEnhetClient = BehandlendeEnhetClient(
        azureAdClient = azureAdClient,
        baseUrl = externalMockEnvironment.environment.clients.behandlendeEnhet.baseUrl,
        clientId = externalMockEnvironment.environment.clients.behandlendeEnhet.clientId,
        httpClient = mockHttpClient,
    )

    val redisStore = RedisStore(externalMockEnvironment.environment.redis)

    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        environment = externalMockEnvironment.environment,
        graphApiClient = graphApiClient,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        adRoller = adRoller,
        redisStore = redisStore,
        axsysClient = axsysClient,
        skjermedePersonerPipClient = skjermedePersonerPipClient,
        pdlClient = pdlClient,
        behandlendeEnhetClient = behandlendeEnhetClient,
    )
}
