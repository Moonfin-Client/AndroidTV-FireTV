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
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.home.mediabar.ExoPlayerTrailerView
import org.jellyfin.androidtv.ui.home.mediabar.TrailerPreviewInfo
import org.jellyfin.androidtv.ui.home.mediabar.TrailerResolver
import org.jellyfin.androidtv.util.UUIDUtils
import org.jellyfin.androidtv.util.sdk.ApiClientFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.compose.koinInject
import timber.log.Timber

private const val TRAILER_START_DELAY_MS = 500L
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
	val httpDataSourceFactory = koinInject<HttpDataSource.Factory>()

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

		delay(TRAILER_START_DELAY_MS)

		try {
			val userId = userRepository.currentUser.value?.id ?: return@LaunchedEffect

			val info = withContext(Dispatchers.IO) {
				TrailerResolver.resolveTrailerPreview(
					apiClient = effectiveApi,
					itemId = item.id,
					userId = userId,
				)
			}

			trailerInfo = info
		} catch (e: Exception) {
			Timber.w(e, "SeriesTrailer: Failed to resolve trailer for ${item.name}")
		}
	}

	val info = trailerInfo
	if (info?.streamInfo != null && focused) {
		ExoPlayerTrailerView(
			streamInfo = info.streamInfo,
			startSeconds = info.startSeconds,
			segments = info.segments,
			muted = muted,
			isVisible = true,
			onVideoEnded = { trailerInfo = null },
			dataSourceFactory = if (info.isLocal) httpDataSourceFactory else null,
			modifier = modifier
				.fillMaxSize()
				.clip(JellyfinTheme.shapes.medium),
		)
	}
}

fun isEligibleForTrailerPreview(item: BaseItemDto?): Boolean =
	item?.type == BaseItemKind.SERIES
