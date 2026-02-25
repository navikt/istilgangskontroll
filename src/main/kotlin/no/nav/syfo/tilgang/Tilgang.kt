package no.nav.syfo.tilgang

import no.nav.syfo.domain.Veileder

data class Tilgang(
    val erGodkjent: Boolean = false,
    val erAvslatt: Boolean = !erGodkjent,
    val fullTilgang: Boolean = false,
    val finnfastlegeTilgang: Boolean = false,
    val legacyTilgang: Boolean = false,
) {
    fun utvidMedTilganger(
        veileder: Veileder,
        adRoller: AdRoller,
    ): Tilgang =
        this.copy(
            fullTilgang = veileder.hasFullTilgang(adRoller),
            finnfastlegeTilgang = veileder.hasFinnfastlegeTilgang(adRoller),
            legacyTilgang = veileder.hasLegacyOnlyTilgang(adRoller),
        )
}
