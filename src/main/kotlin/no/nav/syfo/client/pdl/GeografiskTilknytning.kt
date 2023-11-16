package no.nav.syfo.client.pdl

data class GeografiskTilknytning(
    val type: GeografiskTilknytningType,
    val value: String?
)

enum class GeografiskTilknytningType {
    BYDEL,
    KOMMUNE,
    UTLAND,
    UDEFINERT
}
