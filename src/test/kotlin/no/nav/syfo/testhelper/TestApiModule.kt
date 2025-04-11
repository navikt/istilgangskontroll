package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.client.axsys.AxsysClient
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.norg.NorgClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.skjermedepersoner.SkjermedePersonerPipClient
import no.nav.syfo.mocks.getMockHttpClient
import no.nav.syfo.tilgang.AdRoller
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
) {
    val adRoller = AdRoller(env = externalMockEnvironment.environment)

    val mockHttpClient = getMockHttpClient(env = externalMockEnvironment.environment)

    val valkeyConfig = externalMockEnvironment.environment.valkeyConfig
    val valkeyStore = ValkeyStore(
        JedisPool(
            JedisPoolConfig(),
            HostAndPort(valkeyConfig.host, valkeyConfig.port),
            DefaultJedisClientConfig.builder()
                .ssl(valkeyConfig.ssl)
                .password(valkeyConfig.valkeyPassword)
                .build()
        )
    )

    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        valkeyStore = valkeyStore,
        httpClient = mockHttpClient,
    )

    val graphApiClient = GraphApiClient(
        azureAdClient = azureAdClient,
        baseUrl = externalMockEnvironment.environment.clients.graphApiUrl,
        relevantSyfoRoller = adRoller.toList(),
        httpClient = mockHttpClient,
        valkeyStore = valkeyStore,
        adRoller = adRoller,
    )

    val axsysClient = AxsysClient(
        azureAdClient = azureAdClient,
        axsysUrl = externalMockEnvironment.environment.clients.axsys.baseUrl,
        clientId = externalMockEnvironment.environment.clients.axsys.clientId,
        valkeyStore = valkeyStore,
        httpClient = mockHttpClient,
    )

    val skjermedePersonerPipClient = SkjermedePersonerPipClient(
        azureAdClient = azureAdClient,
        skjermedePersonerUrl = externalMockEnvironment.environment.clients.skjermedePersoner.baseUrl,
        clientId = externalMockEnvironment.environment.clients.skjermedePersoner.clientId,
        valkeyStore = valkeyStore,
        httpClient = mockHttpClient,
    )

    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        baseUrl = externalMockEnvironment.environment.clients.pdl.baseUrl,
        clientId = externalMockEnvironment.environment.clients.pdl.clientId,
        valkeyStore = valkeyStore,
        httpClient = mockHttpClient,
    )

    val behandlendeEnhetClient = BehandlendeEnhetClient(
        azureAdClient = azureAdClient,
        baseUrl = externalMockEnvironment.environment.clients.behandlendeEnhet.baseUrl,
        clientId = externalMockEnvironment.environment.clients.behandlendeEnhet.clientId,
        valkeyStore = valkeyStore,
        httpClient = mockHttpClient,
    )

    val norgClient = NorgClient(
        baseUrl = externalMockEnvironment.environment.clients.norgUrl,
        valkeyStore = valkeyStore,
        httpClient = mockHttpClient,
    )

    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        environment = externalMockEnvironment.environment,
        graphApiClient = graphApiClient,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        adRoller = adRoller,
        valkeyStore = valkeyStore,
        axsysClient = axsysClient,
        skjermedePersonerPipClient = skjermedePersonerPipClient,
        pdlClient = pdlClient,
        behandlendeEnhetClient = behandlendeEnhetClient,
        norgClient = norgClient,
    )
}
