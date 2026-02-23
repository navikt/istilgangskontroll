package no.nav.syfo.domain

import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.client.graphapi.Gruppe
import no.nav.syfo.tilgang.AdRolle
import no.nav.syfo.tilgang.AdRoller
import no.nav.syfo.tilgang.Enhet

/**
 * Representerer en veileder med tilhørende AD-grupper og enheter.
 *
 * @property veilederident Identifikator for veilederen, format Z999999.
 * @property token Autentiseringstoken for veilederen, må brukes for å lage OBO-tokens mot andre tjenester.
 * @property adGrupper Liste over AD-grupper veilederen har. Gruppene er både roller og enheter.
 *
 * Kalkulerte verdier:
 * @property enheter Liste over enheter veilederen har tilgang til, utledet fra adGrupper.
 */
data class Veileder(
    val veilederident: String,
    val token: Token,
    val adGrupper: List<Gruppe>,
) {
    val enheter: List<Enhet> =
        this.adGrupper.mapNotNull { gruppe -> gruppe.getEnhetNr()?.let { Enhet(it) } }

    fun hasSyfoTilgang(adRoller: AdRoller): Boolean =
        hasFullTilgang(adRoller) || hasAccessToRole(adRoller.SYFO_LES)

    fun hasFullTilgang(adRoller: AdRoller): Boolean =
        hasAccessToRole(adRoller.SYFO_FULL) || hasAccessToRole(adRoller.SYFO_LEGACY)

    fun hasFinnfastlegeTilgang(adRoller: AdRoller): Boolean =
        hasAccessToRole(adRoller.FINNFASTLEGE) || hasAccessToRole(adRoller.SYFO_LEGACY) ||
            hasAccessToRole(adRoller.SYFO_FULL) || hasAccessToRole(adRoller.SYFO_LES)

    fun hasLegacyOnlyTilgang(adRoller: AdRoller): Boolean =
        hasAccessToRole(adRoller.SYFO_LEGACY) &&
            !(hasAccessToRole(adRoller.SYFO_FULL) || hasAccessToRole(adRoller.SYFO_LES))

    fun hasAccessToRole(adRolle: AdRolle): Boolean =
        this.adGrupper.any { it.uuid == adRolle.id }

    fun hasAccessToEnhet(enhet: Enhet): Boolean =
        this.enheter.any { it.id == enhet.id }
}
