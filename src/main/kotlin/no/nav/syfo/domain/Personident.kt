package no.nav.syfo.domain

import org.slf4j.LoggerFactory

@JvmInline
value class Personident(val value: String) {
    private val elevenDigits: Regex
        get() = Regex("^\\d{11}\$")

    init {
        if (!elevenDigits.matches(value)) {
            throw IllegalArgumentException("Value is not a valid PersonIdentNumber")
        }
    }
}

fun List<String>.filterValidPersonidenter(): List<Personident> {
    return this.mapNotNull {
        try {
            Personident(it)
        } catch (e: IllegalArgumentException) {
            log.error("Received invalid personident: $it")
            null
        }
    }
}

private val log = LoggerFactory.getLogger(Personident::class.java)
