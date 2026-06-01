package no.nav.syfo.client.pdl

data class GeografiskTilknytning(
    val type: GeografiskTilknytningType,
    val value: String?
) {
    fun isKommuneOrBydel(): Boolean {
        return type === GeografiskTilknytningType.BYDEL || type === GeografiskTilknytningType.KOMMUNE
    }

    fun isUtlandOrWithoutGT(): Boolean {
        return type === GeografiskTilknytningType.UTLAND || value == null
    }
}

enum class GeografiskTilknytningType {
    BYDEL,
    KOMMUNE,
    UTLAND,
    UDEFINERT
}
