package no.nav.syfo.tilgang

import no.nav.syfo.application.Environment

class AdRolle(
    val name: String,
    val id: String,
    val rolle: String,
)

class AdRoller(
    val env: Environment
) {
    val KODE6 = AdRolle(
        name = "KODE6",
        id = env.kode6Id,
        rolle = "0000-GA-Strengt_Fortrolig_Adresse",
    )
    val KODE7 = AdRolle(
        name = "KODE7",
        id = env.kode7Id,
        rolle = "0000-GA-Fortrolig_Adresse"
    )
    val SYFO_LEGACY = AdRolle(
        name = "SYFO",
        id = env.legacySyfoTilgangGroupId,
        rolle = "0000-GA-SYFO-SENSITIV",
    )
    val SYFO_FULL = AdRolle(
        name = "MODIA-SYFO-VEILEDER",
        id = env.syfoFullTilgangGroupId,
        rolle = "0000-CA-MODIA-SYFO-VEILEDER",
    )
    val SYFO_LES = AdRolle(
        name = "MODIA-SYFO-LESETILGANG",
        id = env.syfoLeseTilgangGroupId,
        rolle = "0000-CA-MODIA-SYFO-LESETILGANG",
    )
    val FINNFASTLEGE = AdRolle(
        name = "FINNFASTLEGE",
        id = env.finnfastlegeTilgangGroupId,
        rolle = "0000-CA-FINNFASTLEGE",
    )
    val EGEN_ANSATT = AdRolle(
        name = "EGEN_ANSATT",
        id = env.skjermingId,
        rolle = "0000-GA-Egne_ansatte",
    )
    val NASJONAL = AdRolle(
        name = "NASJONAL",
        id = env.nasjonalId,
        rolle = "0000-GA-GOSYS_NASJONAL",
    )
    val REGIONAL = AdRolle(
        name = "REGIONAL",
        id = env.regionalId,
        rolle = "0000-GA-GOSYS_REGIONAL",
    )
    val PAPIRSYKMELDING = AdRolle(
        name = "PAPIRSYKMELDING",
        id = env.papirsykmeldingId,
        rolle = "0000-GA-papirsykmelding",
    )
}
