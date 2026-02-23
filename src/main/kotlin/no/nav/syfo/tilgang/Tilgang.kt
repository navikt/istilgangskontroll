package no.nav.syfo.tilgang

import no.nav.syfo.domain.Veileder

data class Tilgang(
    val erGodkjent: Boolean = false,
    val erAvslatt: Boolean = !erGodkjent,
    val fullTilgang: Boolean = false,
    val finnfastlege: Boolean = false,
    val legacy: Boolean = false,
) {

    fun utvidMedTilganger(
        veileder: Veileder,
        adRoller: AdRoller,
    ): Tilgang =
        this.copy(
            fullTilgang = veileder.hasFullTilgang(adRoller),
            finnfastlege = veileder.hasFinnfastlegeTilgang(adRoller),
            legacy = veileder.hasLegacyOnlyTilgang(adRoller),
        )
}
