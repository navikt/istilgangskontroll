package no.nav.syfo.tilgang

data class Tilgang(
    val erGodkjent: Boolean = false,
    @Deprecated("Byttet navn til erGodkjent, beholdt for tilbakekompabilitet hos konsumenter")
    val harTilgang: Boolean = erGodkjent,
    val erAvslatt: Boolean = !erGodkjent
)
