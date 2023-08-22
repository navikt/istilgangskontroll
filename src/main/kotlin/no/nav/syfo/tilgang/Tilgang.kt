package no.nav.syfo.tilgang

data class Tilgang(
    val erGodkjent: Boolean = false,
    val erAvslatt: Boolean = !erGodkjent
)
