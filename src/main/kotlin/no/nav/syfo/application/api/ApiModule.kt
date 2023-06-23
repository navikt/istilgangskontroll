package no.nav.syfo.application.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.auth.*
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.metric.registerMetricApi
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.wellknown.WellKnown
import no.nav.syfo.tilgang.*

fun Application.apiModule(
    applicationState: ApplicationState,
    graphApiClient: GraphApiClient,
    environment: Environment,
    wellKnownInternalAzureAD: WellKnown,
    adRoller: AdRoller,
    redisStore: RedisStore
) {
    installMetrics()
    installCallId()
    installContentNegotiation()
    installStatusPages()
    installJwtAuthentication(
        jwtIssuerList = listOf(
            JwtIssuer(
                acceptedAudienceList = listOf(environment.azure.appClientId),
                jwtIssuerType = JwtIssuerType.INTERNAL_AZUREAD,
                wellKnown = wellKnownInternalAzureAD,
            ),
        )
    )

    val tilgangService = TilgangService(
        graphApiClient = graphApiClient,
        adRoller = adRoller,
        redisStore = redisStore,
    )

    routing {
        registerPodApi(
            applicationState = applicationState,
        )
        registerMetricApi()
        authenticate(JwtIssuerType.INTERNAL_AZUREAD.name) {
            registerTilgangApi(
                tilgangService = tilgangService,
            )
        }
    }
}
