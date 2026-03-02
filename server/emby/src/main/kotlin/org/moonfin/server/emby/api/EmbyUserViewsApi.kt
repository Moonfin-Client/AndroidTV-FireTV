package org.moonfin.server.emby.api

import org.moonfin.server.core.api.ServerUserViewsApi
import org.moonfin.server.core.model.ServerItem
import org.moonfin.server.emby.EmbyApiClient
import org.moonfin.server.emby.mapper.toServerItem

class EmbyUserViewsApi(private val apiClient: EmbyApiClient) : ServerUserViewsApi {

    private var cachedViews: List<ServerItem>? = null
    private var cachedUserId: String? = null
    private var cacheTimestamp: Long = 0L

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
    }

    override suspend fun getUserViews(userId: String): List<ServerItem> {
        val now = System.currentTimeMillis()
        val cached = cachedViews
        if (cached != null && cachedUserId == userId && now - cacheTimestamp < CACHE_TTL_MS) {
            return cached
        }

        val response = apiClient.userViewsService!!
            .getUsersByUseridViews(userId, includeExternalContent = true)
            .body()
        val views = response.items?.map { it.toServerItem() } ?: emptyList()

        cachedViews = views
        cachedUserId = userId
        cacheTimestamp = now
        return views
    }

    fun invalidateCache() {
        cachedViews = null
        cachedUserId = null
        cacheTimestamp = 0L
    }
}
