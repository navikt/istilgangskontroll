package no.nav.syfo.tilgang

import no.nav.syfo.domain.Veileder

data class Tilgang(
    val erGodkjent: Boolean = false,
    val erAvslatt: Boolean = !erGodkjent,
    val fullTilgang: Boolean = false,
    val finnfastlege: Boolean = false,
    val legacy: Boolean = true,
)

fun Tilgang.utvidMedTilganger(
    veileder: Veileder,
    adRoller: AdRoller,
): Tilgang =
    this.copy(
        fullTilgang = veileder.hasAccessToRole(adRoller.SYFO_FULL) || veileder.hasAccessToRole(adRoller.SYFO_LEGACY),
        finnfastlege = veileder.hasAccessToRole(adRoller.FINNFASTLEGE) || veileder.hasAccessToRole(adRoller.SYFO_LEGACY) ||
            veileder.hasAccessToRole(adRoller.SYFO_FULL) || veileder.hasAccessToRole(adRoller.SYFO_LES),
        legacy = veileder.hasAccessToRole(adRoller.SYFO_LEGACY) && !(veileder.hasAccessToRole(adRoller.SYFO_FULL) || veileder.hasAccessToRole(adRoller.SYFO_LES)),
    )
