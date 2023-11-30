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

fun List<String>.removeInvalidPersonidenter(): List<String> {
    return this.filter {
        try {
            Personident(it)
            true
        } catch (e: IllegalArgumentException) {
            log.error("Received invalid personident: $it")
            false
        }
    }
}

private val log = LoggerFactory.getLogger(Personident::class.java)
