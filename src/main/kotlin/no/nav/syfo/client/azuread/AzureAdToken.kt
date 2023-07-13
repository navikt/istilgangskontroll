package no.nav.syfo.client.azuread

import java.time.LocalDateTime

data class AzureAdToken(
    val accessToken: String,
    val expires: LocalDateTime,
)
fun AzureAdToken.isExpired() = this.expires < LocalDateTime.now().plusSeconds(60)
