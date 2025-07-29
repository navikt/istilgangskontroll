package no.nav.syfo.client.azuread

import com.azure.core.credential.TokenRequestContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AzureAdTokenTest {

    @Test
    fun `Opprette tokenCredential basert på azureAdToken`() {
        val expiresAt = LocalDateTime.of(2025, 7, 14, 10, 0)
        val azureAdToken = AzureAdToken("eyJhbGciOiJIUz...", expiresAt)

        val tokenCredential = azureAdToken.toTokenCredential()
        val accessToken = tokenCredential.getTokenSync(TokenRequestContext())

        assertEquals("eyJhbGciOiJIUz...", accessToken.token)
        assertEquals(expiresAt, accessToken.expiresAt.toLocalDateTime())
    }
}
