package no.nav.syfo.client.graphapi

data class Gruppe(
    val uuid: String,
    val adGruppenavn: String?,
) {
    fun getEnhetNr(): String? = adGruppenavn?.let { regex.find(it) }?.let {
        val (enhetNr) = it.destructured
        enhetNr
    }

    companion object {
        const val ENHETSNAVN_PREFIX = "0000-GA-ENHET_"
        val regex = """^$ENHETSNAVN_PREFIX(\d{4})$""".toRegex()
    }
}
