package no.nav.syfo.domain

import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.client.graphapi.Gruppe
import no.nav.syfo.tilgang.AdRolle
import no.nav.syfo.tilgang.Enhet

data class Veileder(
    val veilederident: String,
    val token: Token,
    val adGrupper: List<Gruppe>,
) {
    val enheter: List<Enhet> =
        this.adGrupper.mapNotNull { gruppe -> gruppe.getEnhetNr()?.let { Enhet(it) } }

    fun hasAccessToRole(adRolle: AdRolle): Boolean =
        this.adGrupper.any { it.uuid == adRolle.id }

    fun hasAccessToEnhet(enhet: Enhet): Boolean =
        this.enheter.any { it.id == enhet.id }
}
