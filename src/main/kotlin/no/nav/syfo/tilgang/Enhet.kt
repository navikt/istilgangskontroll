package no.nav.syfo.tilgang

data class Enhet(val id: String) {
    private val fourDigits = Regex("^\\d{4}\$")

    init {
        if (!fourDigits.matches(id)) {
            throw IllegalArgumentException("Value is not a valid enhet")
        }
    }
}
