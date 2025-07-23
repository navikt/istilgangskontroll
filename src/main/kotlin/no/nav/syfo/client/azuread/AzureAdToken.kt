package no.nav.syfo.client.azuread

import com.azure.core.credential.AccessToken
import com.azure.core.credential.TokenCredential
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.time.ZoneOffset

data class AzureAdToken(
    val accessToken: String,
    val expires: LocalDateTime,
) {
    fun toTokenCredential(): TokenCredential {
        val atOffset = expires.atOffset(ZoneOffset.UTC)
        return TokenCredential { Mono.just(AccessToken(accessToken, atOffset)) }
    }
}

fun AzureAdToken.isExpired() = this.expires < LocalDateTime.now().plusSeconds(60)
