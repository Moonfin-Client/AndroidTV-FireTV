package org.jellyfin.androidtv.data.service.jellyseerr

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

/**
 * Extension functions for converting Jellyseerr models to Jellyfin models
 */

/**
 * Convert a JellyseerrDiscoverItemDto to a BaseItemDto for display in search results
 * This creates a virtual item that can be displayed in the UI and opened in Jellyseerr details
 * 
 * The poster URL is stored in imageTags with a special "jellyseerr://" prefix
 * which will be detected and used directly by the image loading system
 * 
 * The original Jellyseerr data is stored in taglines as JSON for later retrieval
 */
fun JellyseerrDiscoverItemDto.toBaseItemDto(): BaseItemDto {
	val displayTitle = title ?: name ?: "Unknown"
	
	// Store TMDB poster URL with a special prefix in imageTags
	// The URL will be used directly when rendering
	val imageTags = posterPath?.let {
		val tmdbUrl = "https://image.tmdb.org/t/p/w500$it"
		mapOf(ImageType.PRIMARY to tmdbUrl)
	}
	
	// Extract year from release date
	val year = releaseDate?.substringBefore("-")?.toIntOrNull()
	
	// Store the original Jellyseerr data as JSON in taglines for later retrieval
	val jellyseerrJson = Json.encodeToString(JellyseerrDiscoverItemDto.serializer(), this)
	
	return BaseItemDto(
		id = UUID.randomUUID(),
		name = displayTitle,
		type = when (mediaType) {
			"movie" -> BaseItemKind.MOVIE
			"tv" -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		},
		overview = overview,
		communityRating = voteAverage?.toFloat(),
		imageTags = imageTags,
		primaryImageAspectRatio = 0.6666667, // Standard poster ratio (2:3)
		productionYear = year,
		// Store the TMDB URL in serverId as a marker that this is a Jellyseerr item
		serverId = "jellyseerr",
		// Store original Jellyseerr data as JSON
		taglines = listOf(jellyseerrJson),
	)
}

/**
 * Check if a BaseItemDto represents a Jellyseerr item
 */
fun BaseItemDto.isJellyseerrItem(): Boolean = serverId == "jellyseerr"

/**
 * Get the Jellyseerr JSON data from a BaseItemDto
 */
fun BaseItemDto.getJellyseerrJson(): String? = taglines?.firstOrNull()
