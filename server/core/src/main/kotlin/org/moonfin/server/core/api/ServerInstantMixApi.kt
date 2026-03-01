package org.moonfin.server.core.api

import org.moonfin.server.core.model.ItemsResult

interface ServerInstantMixApi {
    suspend fun getInstantMix(itemId: String, userId: String? = null, limit: Int? = null): ItemsResult
}
