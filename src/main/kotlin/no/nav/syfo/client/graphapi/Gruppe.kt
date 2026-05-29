package no.nav.syfo.client.graphapi

data class Gruppe(
    val uuid: String,
    val adGruppenavn: String?,
) {
    fun getEnhetNr(): String? = adGruppenavn?.let { enhetRegex.find(it) }?.let {
        val (enhetNr) = it.destructured
        enhetNr
    }

    fun getGeoKode(): String? = adGruppenavn?.let { geoRegex.find(it) }?.let {
        val (geoKode) = it.destructured
        geoKode
    }

    companion object {
        const val ENHETSNAVN_PREFIX = "0000-GA-ENHET_"
        const val GEO_PREFIX = "0000-GA-GEO_"

        val enhetRegex = """^$ENHETSNAVN_PREFIX(\d{4})$""".toRegex()
        val geoRegex = """^$GEO_PREFIX(\d{4}|\d{6})$""".toRegex()
    }
}
