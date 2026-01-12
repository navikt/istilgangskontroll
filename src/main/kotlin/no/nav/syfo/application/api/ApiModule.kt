package no.nav.syfo.application.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.auth.JwtIssuer
import no.nav.syfo.application.api.auth.JwtIssuerType
import no.nav.syfo.application.api.auth.installJwtAuthentication
import no.nav.syfo.cache.ValkeyStore
import no.nav.syfo.application.metric.registerMetricApi
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.norg.NorgClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.skjermedepersoner.SkjermedePersonerPipClient
import no.nav.syfo.client.tilgangsmaskin.TilgangsmaskinClient
import no.nav.syfo.client.wellknown.WellKnown
import no.nav.syfo.tilgang.AdRoller
import no.nav.syfo.tilgang.TilgangService
import no.nav.syfo.tilgang.registerTilgangApi

fun Application.apiModule(
    applicationState: ApplicationState,
    graphApiClient: GraphApiClient,
    environment: Environment,
    wellKnownInternalAzureAD: WellKnown,
    adRoller: AdRoller,
    valkeyStore: ValkeyStore,
    azureAdClient: AzureAdClient,
    skjermedePersonerPipClient: SkjermedePersonerPipClient,
    pdlClient: PdlClient,
    behandlendeEnhetClient: BehandlendeEnhetClient,
    norgClient: NorgClient,
    tilgangsmaskin: TilgangsmaskinClient,
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
        azureAdClient = azureAdClient,
        graphApiClient = graphApiClient,
        skjermedePersonerPipClient = skjermedePersonerPipClient,
        pdlClient = pdlClient,
        behandlendeEnhetClient = behandlendeEnhetClient,
        adRoller = adRoller,
        valkeyStore = valkeyStore,
        norgClient = norgClient,
        tilgangsmaskin = tilgangsmaskin,
    )

    routing {
        registerPodApi(
            applicationState = applicationState,
        )
        registerMetricApi()
        authenticate(JwtIssuerType.INTERNAL_AZUREAD.name) {
            registerTilgangApi(
                tilgangService = tilgangService,
                preAuthorizedApps = environment.azure.preAuthorizedApps,
            )
        }
    }
}
