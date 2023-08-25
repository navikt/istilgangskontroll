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
    val SYFO = AdRolle(
        name = "SYFO",
        id = env.syfoId,
        rolle = "0000-GA-SYFO-SENSITIV",
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

    fun toList(): List<AdRolle> {
        return listOf(
            KODE6,
            KODE7,
            SYFO,
            EGEN_ANSATT,
            NASJONAL,
            REGIONAL,
            PAPIRSYKMELDING,
        )
    }
}
