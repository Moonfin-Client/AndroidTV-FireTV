package org.moonfin.server.core.model

data class GetResumeItemsRequest(
    val userId: String? = null,
    val parentId: String? = null,
    val includeItemTypes: List<ItemType>? = null,
    val fields: List<ItemField>? = null,
    val limit: Int? = null,
    val startIndex: Int? = null,
    val enableImages: Boolean? = null,
    val imageTypeLimit: Int? = null,
)
