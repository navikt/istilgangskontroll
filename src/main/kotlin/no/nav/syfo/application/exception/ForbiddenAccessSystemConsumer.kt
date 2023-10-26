package no.nav.syfo.application.exception

class ForbiddenAccessSystemConsumer(
    consumerClientIdAzp: String,
    message: String = "Consumer with clientId(azp)=$consumerClientIdAzp is denied access to system API",
) : RuntimeException(message)
