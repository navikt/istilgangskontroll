package no.nav.syfo.application

data class ClientsEnvironment(
    val graphApiUrl: String,
    val axsys: ClientEnvironment,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)
