package no.nav.syfo.domain

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
