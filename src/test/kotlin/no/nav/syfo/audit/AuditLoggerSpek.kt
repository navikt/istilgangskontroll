package no.nav.syfo.audit

import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class AuditLoggerSpek : Spek({
    describe("CEF") {
        it("Is compliant with CEF format") {
            val cef = CEF(
                suid = "X123456",
                duid = "01010199999",
                event = AuditLogEvent.Access,
                permit = true,
                appName = "callingApplication",
            )

            cef.toString()
                .startsWith("CEF:0|sykefraværsoppfølging|istilgangskontroll|1.0|audit:access|istilgangskontroll audit log|INFO|") shouldBeEqualTo true
            cef.toString()
                .endsWith("suid=X123456 duid=01010199999 flexString1Label=Decision flexString1=Permit flexString2Label=App flexString2=callingApplication") shouldBeEqualTo true
        }
    }

    describe("AuditLogEvent") {
        it("Returns custom toString for Create") {
            val event = AuditLogEvent.Create

            val eventString = event.toString()

            eventString shouldBeEqualTo "audit:create"
        }

        it("Returns custom toString for Access") {
            val event = AuditLogEvent.Access

            val eventString = event.toString()

            eventString shouldBeEqualTo "audit:access"
        }

        it("Returns custom toString for Update") {
            val event = AuditLogEvent.Update

            val eventString = event.toString()

            eventString shouldBeEqualTo "audit:update"
        }
    }
})
