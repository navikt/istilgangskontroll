package no.nav.syfo.audit

import org.slf4j.LoggerFactory
import java.time.Instant

enum class AuditLogEvent {
    Create,
    Access,
    Update;

    override fun toString(): String {
        return if (this == Create) {
            "audit:create"
        } else if (this == Access) {
            "audit:access"
        } else {
            "audit:update"
        }
    }
}

data class CEF(
    val suid: String, // NAV-ident
    val duid: String, // Personident
    val event: AuditLogEvent,
    val permit: Boolean,
    val appName: String,
) {
    override fun toString() =
        "CEF:0|sykefraværsoppfølging|istilgangskontroll|1.0|$event|istilgangskontroll audit log|INFO|end=${
        Instant.now().toEpochMilli()
        } suid=$suid duid=$duid flexString1Label=Decision flexString1=${if (permit) "Permit" else "Deny"} flexString2Label=App flexString2=$appName"
}

private val auditLogger = LoggerFactory.getLogger("auditLogger")

fun auditLog(format: CEF) {
    auditLogger.info("$format")
}
