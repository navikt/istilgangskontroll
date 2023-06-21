package no.nav.syfo.application

data class ClientsEnvironment(
    val graphApiUrl: String,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)
