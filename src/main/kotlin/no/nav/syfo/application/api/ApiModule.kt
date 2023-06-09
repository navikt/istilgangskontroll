package no.nav.syfo.application.api

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.nav.syfo.application.ApplicationState

fun Application.apiModule(
    applicationState: ApplicationState,

) {
    installMetrics()
    installCallId()
    installContentNegotiation()
    installStatusPages()

    routing {
        registerPodApi(
            applicationState = applicationState,
        )
    }
}
