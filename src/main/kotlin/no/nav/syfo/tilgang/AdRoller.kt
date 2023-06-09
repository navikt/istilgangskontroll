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
    val OLD_KODE6 = AdRolle(
        name = "KODE6",
        id = env.oldKode6Id,
        rolle = "0000-GA-GOSYS_KODE6",
    )
    val KODE6 = AdRolle(
        name = "KODE6",
        id = env.kode6Id,
        rolle = "0000-GA-Strengt_Fortrolig_Adresse",
    )
    val OLD_KODE7 = AdRolle(
        name = "KODE7",
        id = env.oldKode7Id,
        rolle = "0000-GA-GOSYS_KODE7"
    )
    val KODE7 = AdRolle(
        name = "KODE7",
        id = env.kode7Id,
        rolle = "0000-GA-Fortrolig_Adresse"
    )
    val SYFO = AdRolle(
        name = "SYFO",
        id = env.syfoId,
        rolle = "0000-GA-SYFO-SENSITIV",
    )
    val OLD_EGEN_ANSATT = AdRolle(
        name = "EGEN_ANSATT",
        id = env.oldSkjermingId,
        rolle = "0000-GA-GOSYS_UTVIDET",
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
    val UTVIDBAR_TIL_NASJONAL = AdRolle(
        name = "UTVIDBAR_TIL_NASJONAL",
        id = env.utvidbarNasjonalId,
        rolle = "0000-GA-GOSYS_UTVIDBAR_TIL_NASJONAL",
    )
    val REGIONAL = AdRolle(
        name = "REGIONAL",
        id = env.regionalId,
        rolle = "0000-GA-GOSYS_REGIONAL",
    )
    val UTVIDBAR_TIL_REGIONAL = AdRolle(
        name = "UTVIDBAR_TIL_REGIONAL",
        id = env.utvidbarRegionalId,
        rolle = "0000-GA-GOSYS_UTVIDBAR_TIL_REGIONAL",
    )
    val PAPIRSYKMELDING = AdRolle(
        name = "PAPIRSYKMELDING",
        id = env.papirsykmeldingId,
        rolle = "0000-GA-papirsykmelding",
    )

    fun toList(): List<AdRolle> {
        return listOf(
            OLD_KODE6,
            KODE6,
            OLD_KODE7,
            KODE7,
            SYFO,
            OLD_EGEN_ANSATT,
            EGEN_ANSATT,
            NASJONAL,
            UTVIDBAR_TIL_NASJONAL,
            REGIONAL,
            UTVIDBAR_TIL_REGIONAL,
            PAPIRSYKMELDING,
        )
    }
}
