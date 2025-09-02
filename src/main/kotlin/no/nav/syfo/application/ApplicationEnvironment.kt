package no.nav.syfo.application

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.server.application.*
import no.nav.syfo.cache.ValkeyConfig
import no.nav.syfo.client.azuread.AzureEnvironment
import no.nav.syfo.util.configuredJacksonMapper
import java.net.URI

data class Environment(

    val azure: AzureEnvironment = AzureEnvironment(
        appClientId = getEnvVar("AZURE_APP_CLIENT_ID"),
        appClientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
        preAuthorizedApps = configuredJacksonMapper().readValue(getEnvVar("AZURE_APP_PRE_AUTHORIZED_APPS")),
        appWellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
        openidConfigTokenEndpoint = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    ),
    val valkeyConfig: ValkeyConfig = ValkeyConfig(
        valkeyUri = URI(getEnvVar("VALKEY_URI_CACHE")),
        valkeyDB = 0, // se https://github.com/navikt/istilgangskontroll/blob/master/README.md
        valkeyUsername = getEnvVar("VALKEY_USERNAME_CACHE"),
        valkeyPassword = getEnvVar("VALKEY_PASSWORD_CACHE"),
    ),

    val kode6Id: String = getEnvVar("ROLE_KODE6_ID"),
    val kode7Id: String = getEnvVar("ROLE_KODE7_ID"),
    val syfoId: String = getEnvVar("ROLE_SYFO_ID"),
    val skjermingId: String = getEnvVar("ROLE_SKJERMING_ID"),
    val nasjonalId: String = getEnvVar("ROLE_NASJONAL_ID"),
    val regionalId: String = getEnvVar("ROLE_REGIONAL_ID"),
    val papirsykmeldingId: String = getEnvVar("ROLE_PAPIRSYKMELDING_ID"),

    val clients: ClientsEnvironment = ClientsEnvironment(
        graphApiUrl = getEnvVar("GRAPHAPI_URL"),
        skjermedePersoner = ClientEnvironment(
            baseUrl = getEnvVar("SKJERMEDE_PERSONER_URL"),
            clientId = getEnvVar("SKJERMEDE_PERSONER_CLIENT_ID")
        ),
        tilgangsmaskin = ClientEnvironment(
            baseUrl = getEnvVar("TILGANGSMASKIN_URL"),
            clientId = getEnvVar("TILGANGSMASKIN_CLIENT_ID")
        ),
        pdl = ClientEnvironment(
            baseUrl = getEnvVar("PDL_URL"),
            clientId = getEnvVar("PDL_CLIENT_ID")
        ),
        behandlendeEnhet = ClientEnvironment(
            baseUrl = getEnvVar("SYFOBEHANDLENDEENHET_URL"),
            clientId = getEnvVar("SYFOBEHANDLENDEENHET_CLIENT_ID")
        ),
        norgUrl = getEnvVar("NORG2_URL"),
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
