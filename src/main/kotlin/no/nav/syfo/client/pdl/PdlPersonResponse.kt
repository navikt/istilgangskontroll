package no.nav.syfo.client.pdl

import java.io.Serializable

data class PdlPersonResponse(
    val errors: List<PdlError>?,
    val data: PdlHentPerson?,
)

data class PdlHentPerson(
    val hentPerson: PdlPerson?,
) : Serializable

data class PdlPerson(
    val adressebeskyttelse: List<Adressebeskyttelse>?,
) : Serializable

data class Adressebeskyttelse(
    val gradering: Gradering,
) : Serializable

enum class Gradering : Serializable {
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    FORTROLIG,
    UGRADERT,
}

fun PdlHentPerson.isKode6(): Boolean {
    val adressebeskyttelse = this.hentPerson?.adressebeskyttelse
    return if (adressebeskyttelse.isNullOrEmpty()) {
        false
    } else {
        return adressebeskyttelse.any {
            it.gradering.isKode6()
        }
    }
}

fun PdlHentPerson.isKode7(): Boolean {
    val adressebeskyttelse = this.hentPerson?.adressebeskyttelse
    return if (adressebeskyttelse.isNullOrEmpty()) {
        false
    } else {
        return adressebeskyttelse.any {
            it.gradering.isKode7()
        }
    }
}

fun Gradering.isKode6(): Boolean {
    return this == Gradering.STRENGT_FORTROLIG || this == Gradering.STRENGT_FORTROLIG_UTLAND
}

fun Gradering.isKode7(): Boolean {
    return this == Gradering.FORTROLIG
}
