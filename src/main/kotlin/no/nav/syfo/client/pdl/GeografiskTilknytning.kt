package no.nav.syfo.client.pdl

data class GeografiskTilknytning(
    val type: GeografiskTilknytningType,
    val value: String?
)

fun GeografiskTilknytning.isUtlandOrWithoutGT() = type == GeografiskTilknytningType.UTLAND || value == null

fun GeografiskTilknytning.kommunekode(): String = value!!.take(4)

enum class GeografiskTilknytningType {
    BYDEL,
    KOMMUNE,
    UTLAND,
    UDEFINERT
}
