package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.cache.ValkeyStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.commonHttpClient
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.norg.NorgClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.proxyHttpClient
import no.nav.syfo.client.skjermedepersoner.SkjermedePersonerPipClient
import no.nav.syfo.client.tilgangsmaskin.TilgangsmaskinClient
import no.nav.syfo.client.wellknown.getWellKnown
import no.nav.syfo.tilgang.AdRoller
import org.slf4j.LoggerFactory
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val logger = LoggerFactory.getLogger("ktor.application")
    val environment = Environment()

    val adRoller = AdRoller(env = environment)
    val valkeyConfig = environment.valkeyConfig
    val valkeyStore = ValkeyStore(
        JedisPool(
            JedisPoolConfig().also {
                it.setMaxTotal(32) // default is 8
                it.setMaxIdle(32) // default is 8
                it.setMinIdle(16) // default is 0
            },
            HostAndPort(valkeyConfig.host, valkeyConfig.port),
            DefaultJedisClientConfig.builder()
                .ssl(valkeyConfig.ssl)
                .user(valkeyConfig.valkeyUsername)
                .password(valkeyConfig.valkeyPassword)
                .database(valkeyConfig.valkeyDB)
                .build()
        )
    )

    val azureAdClient = AzureAdClient(
        azureEnvironment = environment.azure,
        valkeyStore = valkeyStore,
        proxyHttpClient = proxyHttpClient,
    )

    val graphApiClient = GraphApiClient(
        azureAdClient = azureAdClient,
        baseUrl = environment.clients.graphApiUrl,
        valkeyStore = valkeyStore,
        adRoller = adRoller,
    )

    val skjermedePersonerPipClient = SkjermedePersonerPipClient(
        azureAdClient = azureAdClient,
        skjermedePersonerUrl = environment.clients.skjermedePersoner.baseUrl,
        valkeyStore = valkeyStore,
        clientId = environment.clients.skjermedePersoner.clientId,
        httpClient = commonHttpClient,
    )

    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        baseUrl = environment.clients.pdl.baseUrl,
        clientId = environment.clients.pdl.clientId,
        valkeyStore = valkeyStore,
        httpClient = commonHttpClient,
    )

    val behandlendeEnhetClient = BehandlendeEnhetClient(
        azureAdClient = azureAdClient,
        baseUrl = environment.clients.behandlendeEnhet.baseUrl,
        clientId = environment.clients.behandlendeEnhet.clientId,
        valkeyStore = valkeyStore,
        httpClient = commonHttpClient,
    )

    val norgClient = NorgClient(
        baseUrl = environment.clients.norgUrl,
        valkeyStore = valkeyStore,
        httpClient = commonHttpClient,
    )

    val tilgangsmaskin = TilgangsmaskinClient(
        azureAdClient = azureAdClient,
        baseUrl = environment.clients.tilgangsmaskin.baseUrl,
        clientId = environment.clients.tilgangsmaskin.clientId,
        httpClient = commonHttpClient,
    )

    val wellKnownInternalAzureAD = getWellKnown(
        wellKnownUrl = environment.azure.appWellKnownUrl,
    )

    val applicationEnvironment = applicationEnvironment {
        log = logger
        config = HoconApplicationConfig(ConfigFactory.load())
    }

    val server = embeddedServer(
        Netty,
        environment = applicationEnvironment,
        configure = {
            connector {
                port = applicationPort
            }
            connectionGroupSize = 16
            workerGroupSize = 32
            callGroupSize = 32
        },
        module = {
            apiModule(
                applicationState = applicationState,
                environment = environment,
                graphApiClient = graphApiClient,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD,
                adRoller = adRoller,
                valkeyStore = valkeyStore,
                azureAdClient = azureAdClient,
                skjermedePersonerPipClient = skjermedePersonerPipClient,
                pdlClient = pdlClient,
                behandlendeEnhetClient = behandlendeEnhetClient,
                norgClient = norgClient,
                tilgangsmaskin = tilgangsmaskin,
            )
            monitor.subscribe(ApplicationStarted) {
                applicationState.ready = true
                logger.info("Application is ready, running Java VM ${Runtime.version()}")
            }
        }
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            applicationState.ready = false
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = true)
}
