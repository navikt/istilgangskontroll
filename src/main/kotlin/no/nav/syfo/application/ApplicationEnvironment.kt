package no.nav.syfo.application

import io.ktor.server.application.*
import no.nav.syfo.cache.RedisEnvironment
import no.nav.syfo.client.azuread.AzureEnvironment

data class Environment(

    val azure: AzureEnvironment = AzureEnvironment(
        appClientId = getEnvVar("AZURE_APP_CLIENT_ID"),
        appClientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
        appWellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
        openidConfigTokenEndpoint = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    ),

    val redis: RedisEnvironment = RedisEnvironment(
        host = getEnvVar("REDIS_HOST"),
        port = getEnvVar("REDIS_PORT", "6379").toInt(),
        secret = getEnvVar("REDIS_PASSWORD"),
    ),

    val oldKode6Id: String = getEnvVar("OLD_ROLE_KODE6_ID"),
    val kode6Id: String = getEnvVar("ROLE_KODE6_ID"),
    val oldKode7Id: String = getEnvVar("OLD_ROLE_KODE7_ID"),
    val kode7Id: String = getEnvVar("ROLE_KODE7_ID"),
    val syfoId: String = getEnvVar("ROLE_SYFO_ID"),
    val oldSkjermingId: String = getEnvVar("OLD_ROLE_SKJERMING_ID"),
    val skjermingId: String = getEnvVar("ROLE_SKJERMING_ID"),
    val nasjonalId: String = getEnvVar("ROLE_NASJONAL_ID"),
    val utvidbarNasjonalId: String = getEnvVar("ROLE_UTVIDBAR_NASJONAL_ID"),
    val regionalId: String = getEnvVar("ROLE_REGIONAL_ID"),
    val utvidbarRegionalId: String = getEnvVar("ROLE_UTVIDBAR_REGIONAL_ID"),
    val papirsykmeldingId: String = getEnvVar("ROLE_PAPIRSYKMELDING_ID"),

    val clients: ClientsEnvironment = ClientsEnvironment(
        graphApiUrl = getEnvVar("GRAPHAPI_URL"),
        axsys = ClientEnvironment(
            baseUrl = getEnvVar("AXSYS_URL"),
            clientId = getEnvVar("AXSYS_CLIENT_ID")
        ),
        skjermedePersoner = ClientEnvironment(
            baseUrl = getEnvVar("SKJERMEDE_PERSONER_URL"),
            clientId = getEnvVar("SKJERMEDE_PERSONER_CLIENT_ID")
        )
    ),

)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.isDev(block: () -> Unit) {
    if (envKind == "dev") block()
}

fun Application.isProd(block: () -> Unit) {
    if (envKind == "production") block()
}
