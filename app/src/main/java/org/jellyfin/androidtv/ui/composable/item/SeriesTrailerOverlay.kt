package org.jellyfin.androidtv.ui.composable.item

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.home.mediabar.TrailerPreviewInfo
import org.jellyfin.androidtv.ui.home.mediabar.TrailerResolver
import org.jellyfin.androidtv.ui.home.mediabar.YouTubeTrailerWebView
import org.jellyfin.androidtv.util.UUIDUtils
import org.jellyfin.androidtv.util.sdk.ApiClientFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.compose.koinInject
import timber.log.Timber

/** Delay before starting trailer resolution to debounce quick scrolling. */
private const val TRAILER_START_DELAY_MS = 500L

/**
 * A composable overlay that plays a muted YouTube trailer preview of a Series
 * directly on top of its card when focused.
 *
 * When [focused] becomes true:
 *  1. Waits [TRAILER_START_DELAY_MS] to avoid triggering on quick scrolls
 *  2. Resolves a YouTube trailer via [TrailerResolver]
 *  3. Plays the trailer muted via [YouTubeTrailerWebView], fading in over the poster
 *
 * When [focused] becomes false, the WebView is disposed.
 *
 * @param item The BaseItemDto (Series) to preview
 * @param focused Whether the card is currently focused
 * @param modifier Compose modifier
 */
@Composable
fun SeriesTrailerOverlay(
	item: BaseItemDto,
	focused: Boolean,
	muted: Boolean = true,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val apiClientFactory = koinInject<ApiClientFactory>()
	val userRepository = koinInject<UserRepository>()

	var trailerInfo by remember { mutableStateOf<TrailerPreviewInfo?>(null) }

	val effectiveApi = remember(item.serverId) {
		val serverId = UUIDUtils.parseUUID(item.serverId)
		if (serverId != null) apiClientFactory.getApiClientForServer(serverId) ?: api else api
	}

	LaunchedEffect(focused, item.id) {
		if (!focused) {
			trailerInfo = null
			return@LaunchedEffect
		}

		Timber.d("SeriesTrailer: Card focused for ${item.name}, waiting ${TRAILER_START_DELAY_MS}ms")

		delay(TRAILER_START_DELAY_MS)

		Timber.d("SeriesTrailer: Resolving trailer for ${item.name}")

		try {
			val userId = userRepository.currentUser.value?.id
			if (userId == null) {
				Timber.w("SeriesTrailer: No current user, cannot resolve trailer")
				return@LaunchedEffect
			}

			val info = withContext(Dispatchers.IO) {
				TrailerResolver.resolveTrailerPreview(
					apiClient = effectiveApi,
					itemId = item.id,
					userId = userId,
				)
			}

			if (info != null) {
				Timber.d("SeriesTrailer: Trailer resolved for ${item.name}: ${info.youtubeVideoId}")
				trailerInfo = info
			} else {
				Timber.d("SeriesTrailer: No trailer available for ${item.name}")
			}
		} catch (e: Exception) {
			Timber.w(e, "SeriesTrailer: Failed to resolve trailer for ${item.name}")
		}
	}

	val info = trailerInfo
	if (info != null && focused) {
		YouTubeTrailerWebView(
			videoId = info.youtubeVideoId,
			startSeconds = info.startSeconds,
			segments = info.segments,
			muted = muted,
			isVisible = true,
			onVideoEnded = { trailerInfo = null },
			modifier = modifier
				.fillMaxSize()
				.clip(JellyfinTheme.shapes.medium),
		)
	}
}

/** Whether the given item type supports trailer preview. */
fun isEligibleForTrailerPreview(item: BaseItemDto?): Boolean =
	item?.type == BaseItemKind.SERIES
