package no.nav.syfo.application

import com.fasterxml.jackson.module.kotlin.readValue
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

    val legacySyfoTilgangGroupId: String = getEnvVar("ROLE_SYFO_ID"),
    val syfoFullTilgangGroupId: String = getEnvVar("ROLE_MODIA_SYFO_VEILEDER_ID"),
    val syfoLeseTilgangGroupId: String = getEnvVar("ROLE_MODIA_SYFO_LESETILGANG_ID"),
    val syfoLeseTilgangMidlertidigGroupId: String = getEnvVar("ROLE_MODIA_SYFO_LESETILGANG_MIDLERTIDIG_ID"),
    val finnfastlegeTilgangGroupId: String = getEnvVar("ROLE_FINNFASTLEGE_ID"),

    val papirsykmeldingId: String = getEnvVar("ROLE_PAPIRSYKMELDING_ID"),

    val clients: ClientsEnvironment = ClientsEnvironment(
        graphApiUrl = getEnvVar("GRAPHAPI_URL"),
        tilgangsmaskin = ClientEnvironment(
            baseUrl = getEnvVar("TILGANGSMASKIN_URL"),
            clientId = getEnvVar("TILGANGSMASKIN_CLIENT_ID")
        ),
    ),
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
