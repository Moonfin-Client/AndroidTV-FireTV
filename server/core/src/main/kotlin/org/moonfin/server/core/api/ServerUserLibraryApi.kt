package org.moonfin.server.core.api

import org.moonfin.server.core.model.ServerItem
import org.moonfin.server.core.model.UserItemData

interface ServerUserLibraryApi {
    suspend fun getItem(itemId: String): ServerItem
    suspend fun markFavorite(itemId: String, userId: String): UserItemData
    suspend fun unmarkFavorite(itemId: String, userId: String): UserItemData
    suspend fun markPlayed(itemId: String, userId: String): UserItemData
    suspend fun unmarkPlayed(itemId: String, userId: String): UserItemData
}
