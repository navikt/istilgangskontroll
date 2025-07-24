package no.nav.syfo.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AuditLoggerTest {

    @Nested
    @DisplayName("CEF")
    inner class CEF {

        @Test
        fun `Is compliant with CEF format`() {
            val cef = CEF(
                suid = "X123456",
                duid = "01010199999",
                event = AuditLogEvent.Access,
                permit = true,
                appName = "callingApplication",
            )

            assertTrue(
                cef.toString()
                    .startsWith("CEF:0|sykefraværsoppfølging|istilgangskontroll|1.0|audit:access|istilgangskontroll audit log|INFO|")
            )
            assertTrue(
                cef.toString()
                    .endsWith("suid=X123456 duid=01010199999 flexString1Label=Decision flexString1=Permit flexString2Label=App flexString2=callingApplication")
            )
        }
    }

    @Nested
    @DisplayName("AuditLogEvent")
    inner class AuditLogEventTests {

        @Test
        fun `Returns custom toString for Create`() {
            val event = AuditLogEvent.Create

            val eventString = event.toString()

            assertEquals("audit:create", eventString)
        }

        @Test
        fun `Returns custom toString for Access`() {
            val event = AuditLogEvent.Access

            val eventString = event.toString()

            assertEquals("audit:access", eventString)
        }

        @Test
        fun `Returns custom toString for Update`() {
            val event = AuditLogEvent.Update

            val eventString = event.toString()

            assertEquals("audit:update", eventString)
        }
    }
}
