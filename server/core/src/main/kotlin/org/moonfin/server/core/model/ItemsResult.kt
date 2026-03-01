package org.moonfin.server.core.model

data class ItemsResult(
    val items: List<ServerItem>,
    val totalRecordCount: Int,
    val startIndex: Int,
)
