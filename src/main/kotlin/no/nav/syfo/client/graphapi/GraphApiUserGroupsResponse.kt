package no.nav.syfo.client.graphapi

data class GraphApiGroup(
    val id: String,
    val displayName: String?,
    val mailNickname: String?,
) {
    // TODO: Dobbeltsjekke at formatet er riktig
    val regex = """\d{4}-GA-ENHET_(\d{4})""".toRegex()

    // TODO: Må ha en test på større deler av dette slik at andre grupper som
    // ikke er enheter ikke skaper problemer
    // 0000-GA-ENHET_0123
    fun getEnhetNr(): String {
        return displayName?.let { regex.find(it) }?.let {
            val (enhetNr) = it.destructured
            enhetNr
        } ?: ""
    }

    companion object {
        // TODO: Ta i bruk
        const val ENHETSNAVN_PREFIX = "0000-GA-ENHET_"
    }
}

data class GraphApiUserGroupsResponse(
    val value: List<GraphApiGroup>,
)
