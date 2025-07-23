package no.nav.syfo.client.graphapi

data class GraphApiGroup(
    val id: String,
    val displayName: String?,
    val mailNickname: String?,
) {
    fun getEnhetNr(): String = displayName?.let { regex.find(it) }?.let {
        val (enhetNr) = it.destructured
        enhetNr
    } ?: ""

    companion object {
        const val ENHETSNAVN_PREFIX = "0000-GA-ENHET_"
        val regex = """$ENHETSNAVN_PREFIX(\d{4})""".toRegex()
    }
}

data class GraphApiUserGroupsResponse(
    val value: List<GraphApiGroup>,
)
