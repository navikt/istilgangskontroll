package no.nav.syfo.application

data class ClientsEnvironment(
    val graphApiUrl: String,
    val tilgangsmaskin: ClientEnvironment,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)
