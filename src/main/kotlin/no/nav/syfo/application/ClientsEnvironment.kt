package no.nav.syfo.application

data class ClientsEnvironment(
    val graphApiUrl: String,
    val axsys: ClientEnvironment,
    val skjermedePersoner: ClientEnvironment,
    val pdl: ClientEnvironment,
    val tilgangsmaskin: ClientEnvironment,
    val behandlendeEnhet: ClientEnvironment,
    val norgUrl: String,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)
