package no.nav.syfo.cache

data class RedisEnvironment(
    val host: String,
    val port: Int,
    val secret: String,
)
