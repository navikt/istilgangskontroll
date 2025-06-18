package no.nav.syfo.client.graphapi

data class GraphApiGroup(
    val id: String,
    val displayName: String?,
    val mailNickname: String?,

) {
    override fun toString(): String {
        return "GraphApiGroup(id='$id', displayName=$displayName, mailNickname=$mailNickname)\n"
    }
}

data class GraphApiUserGroupsResponse(
    val value: List<GraphApiGroup>,
) {
    override fun toString(): String {
        return "GraphApiUserGroupsResponse(value=$value)"
    }
}
