package no.nav.syfo.client.azuread

import com.azure.core.credential.TokenRequestContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AzureAdTokenTest {

    @Test
    fun `Opprette tokenCredential basert på azureAdToken (sommertid)`() {
        val expiresAt = LocalDateTime.of(2025, 7, 14, 10, 0)
        val azureAdToken = AzureAdToken("eyJhbGciOiJIUz...", expiresAt)

        val tokenCredential = azureAdToken.toTokenCredential()

        val accessToken = tokenCredential.getTokenSync(TokenRequestContext())
        val expiresAtUTC = OffsetDateTime.of(2025, 7, 14, 8, 0, 0, 0, ZoneOffset.UTC)
        assertEquals("eyJhbGciOiJIUz...", accessToken.token)
        assertEquals(expiresAtUTC, accessToken.expiresAt)
    }

    @Test
    fun `Opprette tokenCredential basert på azureAdToken (vintertid)`() {
        val expiresAt = LocalDateTime.of(2025, 11, 14, 10, 0)
        val azureAdToken = AzureAdToken("eyJhbGciOiJIUz...", expiresAt)

        val tokenCredential = azureAdToken.toTokenCredential()

        val accessToken = tokenCredential.getTokenSync(TokenRequestContext())
        val expiresAtUTC = OffsetDateTime.of(2025, 11, 14, 9, 0, 0, 0, ZoneOffset.UTC)
        assertEquals("eyJhbGciOiJIUz...", accessToken.token)
        assertEquals(expiresAtUTC, accessToken.expiresAt)
    }
}
