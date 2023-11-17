package no.nav.syfo.client.pdl

data class PipPersondataResponse(
    val person: PipPerson,
    val identer: PipIdenter,
    val geografiskTilknytning: PipGeografiskTilknytning?,
)

data class PipPerson(
    val adressebeskyttelse: List<PipAdressebeskyttelse>,
    val doedsfall: List<PipDoedsfall>,
)

data class PipAdressebeskyttelse(
    val gradering: Gradering,
)

enum class Gradering {
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    FORTROLIG,
    UGRADERT,
}

fun PipPersondataResponse.isKode6(): Boolean {
    val adressebeskyttelse = this.person.adressebeskyttelse
    return if (adressebeskyttelse.isNullOrEmpty()) {
        false
    } else {
        adressebeskyttelse.any {
            it.gradering.isKode6()
        }
    }
}

fun PipPersondataResponse.isKode7(): Boolean {
    val adressebeskyttelse = this.person.adressebeskyttelse
    return if (adressebeskyttelse.isNullOrEmpty()) {
        false
    } else {
        adressebeskyttelse.any {
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

data class PipDoedsfall(
    val doedsdato: String? = null,
)

data class PipIdenter(
    val identer: List<PipIdent>,
)

data class PipIdent(
    val ident: String?,
    val historisk: Boolean?,
    val gruppe: String?,
)

data class PipGeografiskTilknytning(
    val gtType: String?,
    val gtBydel: String?,
    val gtKommune: String?,
    val gtLand: String?,
)

enum class PdlGeografiskTilknytningType {
    BYDEL,
    KOMMUNE,
    UTLAND,
    UDEFINERT,
}

fun PipGeografiskTilknytning.geografiskTilknytning(): GeografiskTilknytning? {
    return this.let { gt ->
        when (gt.gtType) {
            PdlGeografiskTilknytningType.BYDEL.name -> {
                GeografiskTilknytning(
                    type = GeografiskTilknytningType.valueOf(PdlGeografiskTilknytningType.BYDEL.name),
                    value = gt.gtBydel,
                )
            }
            PdlGeografiskTilknytningType.KOMMUNE.name -> {
                GeografiskTilknytning(
                    type = GeografiskTilknytningType.valueOf(PdlGeografiskTilknytningType.KOMMUNE.name),
                    value = gt.gtKommune,
                )
            }
            PdlGeografiskTilknytningType.UTLAND.name -> {
                GeografiskTilknytning(
                    type = GeografiskTilknytningType.valueOf(PdlGeografiskTilknytningType.UTLAND.name),
                    value = gt.gtLand,
                )
            }
            else -> {
                GeografiskTilknytning(
                    type = GeografiskTilknytningType.valueOf(PdlGeografiskTilknytningType.UDEFINERT.name),
                    value = null,
                )
            }
        }
    }
}
