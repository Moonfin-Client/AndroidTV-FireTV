package org.moonfin.server.emby.api

import org.emby.client.model.UserItemDataDto
import org.moonfin.server.core.api.ServerUserLibraryApi
import org.moonfin.server.core.model.ServerItem
import org.moonfin.server.core.model.UserItemData
import org.moonfin.server.emby.EmbyApiClient
import org.moonfin.server.emby.mapper.toServerItem
import org.moonfin.server.emby.mapper.toUserItemData

class EmbyUserLibraryApi(private val apiClient: EmbyApiClient) : ServerUserLibraryApi {

    override suspend fun getItem(itemId: String): ServerItem {
        val userId = apiClient.userId ?: error("EmbyUserLibraryApi.getItem: userId not configured")
        val dto = apiClient.userLibraryService!!.getUsersByUseridItemsById(
            userId = userId,
            id = itemId,
        ).body()
        return dto.toServerItem()
    }

    override suspend fun markFavorite(itemId: String, userId: String): UserItemData {
        val dto: UserItemDataDto = apiClient.userLibraryService!!.postUsersByUseridFavoriteitemsById(
            userId = userId,
            id = itemId,
        ).body()
        return dto.toUserItemData()
    }

    override suspend fun unmarkFavorite(itemId: String, userId: String): UserItemData {
        val dto: UserItemDataDto = apiClient.userLibraryService!!.deleteUsersByUseridFavoriteitemsById(
            userId = userId,
            id = itemId,
        ).body()
        return dto.toUserItemData()
    }

    override suspend fun markPlayed(itemId: String, userId: String): UserItemData {
        val dto: UserItemDataDto = apiClient.playstateService!!.postUsersByUseridPlayeditemsById(
            userId = userId,
            id = itemId,
            datePlayed = null,
        ).body()
        return dto.toUserItemData()
    }

    override suspend fun unmarkPlayed(itemId: String, userId: String): UserItemData {
        val dto: UserItemDataDto = apiClient.playstateService!!.deleteUsersByUseridPlayeditemsById(
            userId = userId,
            id = itemId,
        ).body()
        return dto.toUserItemData()
    }
}
