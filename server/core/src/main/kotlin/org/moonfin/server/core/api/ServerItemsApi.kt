package org.moonfin.server.core.api

import org.moonfin.server.core.model.GetItemsRequest
import org.moonfin.server.core.model.GetLatestMediaRequest
import org.moonfin.server.core.model.GetNextUpRequest
import org.moonfin.server.core.model.GetResumeItemsRequest
import org.moonfin.server.core.model.ItemsResult
import org.moonfin.server.core.model.ServerItem

interface ServerItemsApi {
    suspend fun getItems(request: GetItemsRequest): ItemsResult
    suspend fun getResumeItems(request: GetResumeItemsRequest): ItemsResult
    suspend fun getLatestMedia(request: GetLatestMediaRequest): List<ServerItem>
    suspend fun getNextUp(request: GetNextUpRequest): ItemsResult
    suspend fun getSimilarItems(itemId: String, limit: Int? = null): ItemsResult
    suspend fun getSeasons(seriesId: String, userId: String): ItemsResult
    suspend fun getEpisodes(seriesId: String, seasonId: String, userId: String): ItemsResult
}
