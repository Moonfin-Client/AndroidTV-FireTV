package org.jellyfin.androidtv.data.model

import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

/**
 * Represents an item (movie, episode, etc.) from a specific server.
 * Includes the ApiClient needed for playback from that server.
 * Used for multi-server content aggregation feature.
 */
data class AggregatedItem(
	val item: BaseItemDto,
	val server: Server,
	val userId: UUID,
	val apiClient: ApiClient
)
