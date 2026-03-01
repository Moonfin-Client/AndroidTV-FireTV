package org.moonfin.server.core.model

data class GetNextUpRequest(
    val userId: String? = null,
    val seriesId: String? = null,
    val fields: List<ItemField>? = null,
    val limit: Int? = null,
    val startIndex: Int? = null,
    val enableImages: Boolean? = null,
    val imageTypeLimit: Int? = null,
)
