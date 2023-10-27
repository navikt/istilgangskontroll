package no.nav.syfo.client.azuread

data class AzureEnvironment(
    val appClientId: String,
    val appClientSecret: String,
    val preAuthorizedApps: List<PreAuthorizedApp>,
    val appWellKnownUrl: String,
    val openidConfigTokenEndpoint: String
)

data class PreAuthorizedApp(
    val name: String,
    val clientId: String
) {
    fun getAppnavn(): String {
        val split = name.split(":")
        return split[2]
    }
}
