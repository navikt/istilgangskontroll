package no.nav.syfo.client.graphapi

data class GraphApiGroup(
    val id: String,
    val displayName: String?,
    val mailNickname: String?,
)

data class GraphApiUserGroupsResponse(
    val value: List<GraphApiGroup>,
)
