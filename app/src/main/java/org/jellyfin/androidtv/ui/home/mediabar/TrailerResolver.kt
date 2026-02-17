package org.jellyfin.androidtv.ui.home.mediabar

import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import java.util.UUID

/**
 * Data class containing the info needed to play a YouTube trailer preview
 * in the media bar.
 */
data class TrailerPreviewInfo(
	val youtubeVideoId: String,
	val startSeconds: Double,
	val segments: List<SponsorBlockApi.Segment>,
)

/**
 * Resolves YouTube trailer video IDs from Jellyfin items and fetches
 * SponsorBlock segments for intelligent start-time calculation.
 */
object TrailerResolver {

	private const val YOUTUBE_HOST = "youtube.com"
	private const val YOUTUBE_SHORT_HOST = "youtu.be"
	private const val YOUTUBE_ID_PARAMETER = "v"
	private const val YOUTUBE_ID_LENGTH = 11

	/**
	 * Extract a YouTube video ID from a URL string.
	 *
	 * Supports formats:
	 * - https://www.youtube.com/watch?v=VIDEO_ID
	 * - https://youtube.com/watch?v=VIDEO_ID
	 * - https://youtu.be/VIDEO_ID
	 * - https://www.youtube.com/embed/VIDEO_ID
	 *
	 * @return The YouTube video ID, or null if not a YouTube URL
	 */
	fun extractYoutubeVideoId(url: String): String? {
		return try {
			val uri = url.toUri()
			val host = uri.host?.lowercase() ?: return null

			when {
				// Standard youtube.com/watch?v=XXX
				host.endsWith(YOUTUBE_HOST) -> {
					val id = uri.getQueryParameter(YOUTUBE_ID_PARAMETER)
					if (id != null && id.length == YOUTUBE_ID_LENGTH) id else {
						// Try /embed/XXX format
						val pathSegments = uri.pathSegments
						val embedIndex = pathSegments.indexOf("embed")
						if (embedIndex >= 0 && embedIndex + 1 < pathSegments.size) {
							val embedId = pathSegments[embedIndex + 1]
							if (embedId.length == YOUTUBE_ID_LENGTH) embedId else null
						} else null
					}
				}
				// Short youtu.be/XXX
				host.endsWith(YOUTUBE_SHORT_HOST) -> {
					val id = uri.lastPathSegment
					if (id != null && id.length == YOUTUBE_ID_LENGTH) id else null
				}
				else -> null
			}
		} catch (e: Exception) {
			Timber.d("TrailerResolver: Failed to parse URL: $url")
			null
		}
	}

	/**
	 * Resolve trailer preview info for a media bar slide item.
	 *
	 * This fetches the item's remote trailers from the Jellyfin API,
	 * extracts any YouTube video ID, then queries SponsorBlock for
	 * skip segments and calculates the optimal start time.
	 *
	 * @param apiClient The Jellyfin API client (for the correct server)
	 * @param itemId The item's UUID
	 * @param userId The user's UUID (needed for the user library API)
	 * @return TrailerPreviewInfo if a YouTube trailer was found, null otherwise
	 */
	suspend fun resolveTrailerPreview(
		apiClient: ApiClient,
		itemId: UUID,
		userId: UUID,
	): TrailerPreviewInfo? = withContext(Dispatchers.IO) {
		try {
			// Fetch the full item to get remoteTrailers
			val item by apiClient.userLibraryApi.getItem(
				itemId = itemId,
				userId = userId,
			)

			resolveTrailerFromItem(item)
		} catch (e: Exception) {
			Timber.w(e, "TrailerResolver: Failed to fetch item $itemId for trailer resolution")
			null
		}
	}

	/**
	 * Resolve trailer info directly from a BaseItemDto that already has remoteTrailers.
	 */
	suspend fun resolveTrailerFromItem(item: BaseItemDto): TrailerPreviewInfo? =
		withContext(Dispatchers.IO) {
			val trailers = item.remoteTrailers.orEmpty()
			if (trailers.isEmpty()) {
				Timber.d("TrailerResolver: No remote trailers for ${item.name}")
				return@withContext null
			}

			// Find the first YouTube trailer URL
			val youtubeVideoId = trailers
				.mapNotNull { trailer -> trailer.url?.let { extractYoutubeVideoId(it) } }
				.firstOrNull()

			if (youtubeVideoId == null) {
				Timber.d("TrailerResolver: No YouTube trailers found for ${item.name}")
				return@withContext null
			}

			Timber.d("TrailerResolver: Found YouTube trailer $youtubeVideoId for ${item.name}")

			// Fetch SponsorBlock segments
			val segments = SponsorBlockApi.getSkipSegments(youtubeVideoId)
			val startSeconds = SponsorBlockApi.calculateStartTime(segments)

			Timber.d("TrailerResolver: SponsorBlock returned ${segments.size} segments, start at ${startSeconds}s")

			TrailerPreviewInfo(
				youtubeVideoId = youtubeVideoId,
				startSeconds = startSeconds,
				segments = segments,
			)
		}
}
