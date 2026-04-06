package org.jellyfin.androidtv.ui.home.mediabar

import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.localizationApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.Locale
import java.util.UUID

data class TrailerPreviewInfo(
	val youtubeVideoId: String? = null,
	val startSeconds: Double = 0.0,
	val segments: List<SponsorBlockApi.Segment> = emptyList(),
	val streamInfo: YouTubeStreamResolver.StreamInfo? = null,
	val isLocal: Boolean = false,
) {
	val previewKey: String get() = youtubeVideoId ?: streamInfo?.videoUrl ?: ""
}

/** Resolves trailer previews for Jellyfin items, preferring local trailers over YouTube. */
object TrailerResolver : KoinComponent {

	private const val YOUTUBE_HOST = "youtube.com"
	private const val YOUTUBE_SHORT_HOST = "youtu.be"
	private const val YOUTUBE_ID_PARAMETER = "v"
	private const val YOUTUBE_ID_LENGTH = 11
	private val user: UserRepository by inject()
	private val api: ApiClient by inject()

	// cache the api response for the cultures contained in:
	// https://github.com/jellyfin/jellyfin/blob/master/Emby.Server.Implementations/Localization/iso6392.txt
	@Volatile
	private var cachedCulturesMap: Map<String, String>? = null
	private val culturesMutex = Mutex()


	fun extractYoutubeVideoId(url: String): String? {
		return try {
			val uri = url.toUri()
			val host = uri.host?.lowercase() ?: return null

			when {
				host.endsWith(YOUTUBE_HOST) -> {
					val id = uri.getQueryParameter(YOUTUBE_ID_PARAMETER)
					if (id != null && id.length == YOUTUBE_ID_LENGTH) id else {
						val pathSegments = uri.pathSegments
						val embedIndex = pathSegments.indexOf("embed")
						if (embedIndex >= 0 && embedIndex + 1 < pathSegments.size) {
							val embedId = pathSegments[embedIndex + 1]
							if (embedId.length == YOUTUBE_ID_LENGTH) embedId else null
						} else null
					}
				}
				host.endsWith(YOUTUBE_SHORT_HOST) -> {
					val id = uri.lastPathSegment
					if (id != null && id.length == YOUTUBE_ID_LENGTH) id else null
				}
				else -> null
			}
		} catch (_: Exception) {
			Timber.d("TrailerResolver: Failed to parse URL: $url")
			null
		}
	}

	suspend fun resolveTrailerPreview(
		apiClient: ApiClient,
		itemId: UUID,
		userId: UUID,
	): TrailerPreviewInfo? = withContext(Dispatchers.IO) {
		try {
			val item by apiClient.userLibraryApi.getItem(
				itemId = itemId,
				userId = userId,
			)

			resolveLocalTrailer(apiClient, item)
				?: resolveYouTubeTrailerFromItem(item)
		} catch (e: Throwable) {
			Timber.w(e, "TrailerResolver: Failed to fetch item $itemId for trailer resolution")
			null
		}
	}

	private suspend fun resolveLocalTrailer(
		apiClient: ApiClient,
		item: BaseItemDto,
	): TrailerPreviewInfo? {
		val localTrailerCount = item.localTrailerCount ?: 0
		if (localTrailerCount < 1) return null

		return try {
			val trailers = apiClient.userLibraryApi.getLocalTrailers(itemId = item.id).content
			val trailer = trailers.firstOrNull() ?: return null

			val streamUrl = apiClient.videosApi.getVideoStreamUrl(
				itemId = trailer.id,
				static = false,
				videoCodec = "h264",
				audioCodec = "aac",
				maxVideoBitDepth = 8,
				audioBitRate = 128000,
				audioChannels = 2,
				subtitleMethod = SubtitleDeliveryMethod.DROP,
			)

			TrailerPreviewInfo(
				streamInfo = YouTubeStreamResolver.StreamInfo(
					videoUrl = streamUrl,
					audioUrl = null,
					isVideoOnly = false,
				),
				isLocal = true,
			)
		} catch (_: Exception) {
			null
		}
	}

	suspend fun resolveTrailerFromItem(item: BaseItemDto): TrailerPreviewInfo? =
		resolveYouTubeTrailerFromItem(item)

	private suspend fun getNormalizedLanguage(preferredLanguage: String?): String? {
		if (preferredLanguage.isNullOrBlank()) return null
		if (preferredLanguage.length <= 2) return preferredLanguage

		val key = preferredLanguage.lowercase(Locale.ROOT)

		try {
			var mapping = cachedCulturesMap
			if (mapping == null) {
				// lock for init
				culturesMutex.withLock {
					mapping = cachedCulturesMap
					if (mapping == null) {
						val map = mutableMapOf<String, String>()
						val cultures = api.localizationApi.getCultures().content
						// transform into map for better lookups
						cultures.forEach { culture ->
							val iso2 = culture.twoLetterIsoLanguageName
							if (iso2.isNotBlank()) {
								culture.threeLetterIsoLanguageNames.forEach { iso3 ->
									map[iso3.lowercase(Locale.ROOT)] = iso2.lowercase(Locale.ROOT)
								}
							}
						}
						mapping = map.toMap() // make immutable
						cachedCulturesMap = mapping
					}
				}
			}
			return mapping?.get(key)
		} catch (e: Exception) {
			Timber.d(e, "TrailerResolver: Failed to fetch cultures for language normalization")
			return null
		}
	}
	private suspend fun resolveYouTubeTrailerFromItem(item: BaseItemDto): TrailerPreviewInfo? =
		withContext(Dispatchers.IO) {
			val trailers = item.remoteTrailers.orEmpty()
			if (trailers.isEmpty()) {
				Timber.d("TrailerResolver: No remote trailers for ${item.name}")
				return@withContext null
			}

			val youtubeVideoId = trailers
				.mapNotNull { trailer -> trailer.url?.let { extractYoutubeVideoId(it) } }
				.firstOrNull()

			if (youtubeVideoId == null) {
				Timber.d("TrailerResolver: No YouTube trailers found for ${item.name}")
				return@withContext null
			}

			Timber.d("TrailerResolver: Found YouTube trailer $youtubeVideoId for ${item.name}")

			val segments = SponsorBlockApi.getSkipSegments(youtubeVideoId)
			val startSeconds = SponsorBlockApi.calculateStartTime(segments)

			Timber.d("TrailerResolver: SponsorBlock returned ${segments.size} segments, start at ${startSeconds}s")

			// get preferred language from user configuration
			val preferredLanguage = user.currentUser.value?.configuration?.audioLanguagePreference
			// look up 2 letter language code to use with resolveStream
			val normalizedLanguage = getNormalizedLanguage(preferredLanguage)
			val streamInfo = YouTubeStreamResolver.resolveStream(
				youtubeVideoId,
				normalizedLanguage
			)
			if (streamInfo == null) {
				Timber.w("TrailerResolver: Could not resolve stream for $youtubeVideoId")
				return@withContext null
			}

			TrailerPreviewInfo(
				youtubeVideoId = youtubeVideoId,
				startSeconds = startSeconds,
				segments = segments,
				streamInfo = streamInfo,
			)
		}
}
