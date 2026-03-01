package org.moonfin.server.core.model

data class GetLatestMediaRequest(
    val userId: String? = null,
    val parentId: String? = null,
    val includeItemTypes: List<ItemType>? = null,
    val fields: List<ItemField>? = null,
    val limit: Int? = null,
    val groupItems: Boolean? = null,
    val imageTypeLimit: Int? = null,
)
