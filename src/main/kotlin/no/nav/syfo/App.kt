package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.axsys.AxsysClient
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.norg.NorgClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.skjermedepersoner.SkjermedePersonerPipClient
import no.nav.syfo.client.wellknown.getWellKnown
import no.nav.syfo.tilgang.AdRoller
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val logger = LoggerFactory.getLogger("ktor.application")
    val environment = Environment()

    val adRoller = AdRoller(env = environment)

    val redisStore = RedisStore(environment.redis)
    val azureAdClient = AzureAdClient(azureEnvironment = environment.azure)

    val graphApiClient = GraphApiClient(
        azureAdClient = azureAdClient,
        baseUrl = environment.clients.graphApiUrl,
        relevantSyfoRoller = adRoller.toList(),
    )

    val axsysClient = AxsysClient(
        azureAdClient = azureAdClient,
        axsysUrl = environment.clients.axsys.baseUrl,
        clientId = environment.clients.axsys.clientId,
        redisStore = redisStore,
    )

    val skjermedePersonerPipClient = SkjermedePersonerPipClient(
        azureAdClient = azureAdClient,
        skjermedePersonerUrl = environment.clients.skjermedePersoner.baseUrl,
        redisStore = redisStore,
        clientId = environment.clients.skjermedePersoner.clientId,
    )

    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        baseUrl = environment.clients.pdl.baseUrl,
        clientId = environment.clients.pdl.clientId,
        redisStore = redisStore,
    )

    val behandlendeEnhetClient = BehandlendeEnhetClient(
        azureAdClient = azureAdClient,
        baseUrl = environment.clients.behandlendeEnhet.baseUrl,
        clientId = environment.clients.behandlendeEnhet.clientId,
        redisStore = redisStore,
    )

    val norgClient = NorgClient(
        baseUrl = environment.clients.norgUrl,
    )

    val wellKnownInternalAzureAD = getWellKnown(
        wellKnownUrl = environment.azure.appWellKnownUrl,
    )

    val applicationEngineEnvironment = applicationEngineEnvironment {
        log = logger
        config = HoconApplicationConfig(ConfigFactory.load())
        connector {
            port = applicationPort
        }
        module {
            apiModule(
                applicationState = applicationState,
                environment = environment,
                graphApiClient = graphApiClient,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD,
                adRoller = adRoller,
                redisStore = redisStore,
                axsysClient = axsysClient,
                skjermedePersonerPipClient = skjermedePersonerPipClient,
                pdlClient = pdlClient,
                behandlendeEnhetClient = behandlendeEnhetClient,
                norgClient = norgClient,
            )
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
        logger.info("Application is ready, running Java VM ${Runtime.version()}")
    }

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
    ) {
        connectionGroupSize = 8
        workerGroupSize = 8
        callGroupSize = 16
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = true)
}
