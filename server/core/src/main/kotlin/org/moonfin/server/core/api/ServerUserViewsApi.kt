package org.moonfin.server.core.api

import org.moonfin.server.core.model.ServerItem

interface ServerUserViewsApi {
    suspend fun getUserViews(userId: String): List<ServerItem>
}
