package no.nav.syfo.testhelper

import no.nav.syfo.client.graphapi.Gruppe
import no.nav.syfo.tilgang.AdRolle
import java.util.*

fun createGruppeForRole(adRolle: AdRolle, adGruppeNavn: String? = null) = Gruppe(
    uuid = adRolle.id,
    adGruppenavn = adGruppeNavn
)

fun createGruppeForEnhet(enhetNr: String) = Gruppe(
    uuid = UUID.randomUUID().toString(),
    adGruppenavn = "0000-GA-ENHET_$enhetNr"
)
