package no.nav.syfo.client.pdl

data class GeografiskTilknytning(
    val type: GeografiskTilknytningType,
    val value: String?
)

fun GeografiskTilknytning.isUtlandOrWithoutGT() = type == GeografiskTilknytningType.UTLAND || value == null

enum class GeografiskTilknytningType {
    BYDEL,
    KOMMUNE,
    UTLAND,
    UDEFINERT
}
