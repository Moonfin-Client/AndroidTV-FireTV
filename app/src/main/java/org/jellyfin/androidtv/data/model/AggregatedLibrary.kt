package org.jellyfin.androidtv.data.model

import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

/**
 * Represents a library from a specific server with display name including server context.
 * Used for multi-server library aggregation feature.
 */
data class AggregatedLibrary(
	val library: BaseItemDto,
	val server: Server,
	val userId: UUID,
	val displayName: String // e.g., "Movies (Server1)"
)
