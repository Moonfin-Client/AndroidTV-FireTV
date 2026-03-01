package org.moonfin.server.emby.api

import org.moonfin.server.core.api.ServerUserViewsApi
import org.moonfin.server.core.model.ServerItem
import org.moonfin.server.emby.EmbyApiClient
import org.moonfin.server.emby.mapper.toServerItem

class EmbyUserViewsApi(private val apiClient: EmbyApiClient) : ServerUserViewsApi {

    override suspend fun getUserViews(userId: String): List<ServerItem> {
        val response = apiClient.userViewsService!!
            .getUsersByUseridViews(userId, includeExternalContent = true)
            .body()
        return response.items?.map { it.toServerItem() } ?: emptyList()
    }
}
