package org.jellyfin.androidtv.ui.composable.item

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.playback.segment.MediaSegmentRepository
import org.jellyfin.androidtv.util.UUIDUtils
import org.jellyfin.androidtv.util.sdk.ApiClientFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.koin.compose.koinInject
import timber.log.Timber

/** Delay before starting preview playback to debounce quick scrolling. */
private const val PREVIEW_START_DELAY_MS = 1500L

/**
 * A composable overlay that plays a muted video preview of an episode/movie
 * directly on top of its card when focused.
 *
 * When [focused] becomes true:
 *  1. Waits [PREVIEW_START_DELAY_MS] to avoid triggering on quick scrolls
 *  2. Resolves a direct play stream URL from Jellyfin
 *  3. Fetches intro segments and seeks past them
 *  4. Starts ExoPlayer muted, fading in over the poster image
 *
 * When [focused] becomes false, the player is stopped and released.
 *
 * @param item The BaseItemDto (episode or movie) to preview
 * @param focused Whether the card is currently focused
 * @param modifier Compose modifier
 */
@OptIn(UnstableApi::class)
@Composable
fun EpisodePreviewOverlay(
	item: BaseItemDto,
	focused: Boolean,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val api = koinInject<ApiClient>()
	val apiClientFactory = koinInject<ApiClientFactory>()
	val mediaSegmentRepository = koinInject<MediaSegmentRepository>()
	val httpDataSourceFactory = koinInject<HttpDataSource.Factory>()

	var streamUrl by remember { mutableStateOf<String?>(null) }
	var seekPositionMs by remember { mutableStateOf(0L) }
	var isPlaying by remember { mutableStateOf(false) }
	var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

	val effectiveApi = remember(item.serverId) {
		val serverId = UUIDUtils.parseUUID(item.serverId)
		if (serverId != null) apiClientFactory.getApiClientForServer(serverId) ?: api else api
	}

	val previewAlpha by animateFloatAsState(
		targetValue = if (isPlaying) 1f else 0f,
		animationSpec = tween(durationMillis = 600),
		label = "previewAlpha",
	)

	LaunchedEffect(focused, item.id) {
		if (!focused) {
			streamUrl = null
			isPlaying = false
			exoPlayer?.stop()
			exoPlayer?.release()
			exoPlayer = null
			return@LaunchedEffect
		}

		Timber.d("EpisodePreview: Card focused for ${item.name} (${item.type}), waiting ${PREVIEW_START_DELAY_MS}ms")

		delay(PREVIEW_START_DELAY_MS)

		Timber.d("EpisodePreview: Resolving stream URL for ${item.name}")

		try {
			val url = withContext(Dispatchers.IO) {
				effectiveApi.videosApi.getVideoStreamUrl(
					itemId = item.id,
					static = true,
				)
			}

			Timber.d("EpisodePreview: Stream URL resolved for ${item.name}: ${url.take(80)}...")

			val introEndMs = withContext(Dispatchers.IO) {
				try {
					val segments = mediaSegmentRepository.getSegmentsForItem(item)
					val introSegment = segments.firstOrNull { it.type == MediaSegmentType.INTRO }
					if (introSegment != null) {
						introSegment.endTicks / 10_000L
					} else {
						val positionTicks = item.userData?.playbackPositionTicks ?: 0L
						if (positionTicks > 0) positionTicks / 10_000L else 0L
					}
				} catch (e: Exception) {
					Timber.w(e, "EpisodePreview: Failed to get intro segments for ${item.name}")
					val positionTicks = item.userData?.playbackPositionTicks ?: 0L
					if (positionTicks > 0) positionTicks / 10_000L else 0L
				}
			}

			streamUrl = url
			seekPositionMs = introEndMs
			Timber.d("EpisodePreview: Ready to play ${item.name}, seekTo=${introEndMs}ms, url=${url.take(80)}...")
		} catch (e: Exception) {
			Timber.w(e, "EpisodePreview: Failed to resolve stream URL for ${item.name}")
		}
	}

	if (streamUrl != null && focused) {
		val currentUrl = streamUrl!!

		DisposableEffect(currentUrl) {
			val renderersFactory = DefaultRenderersFactory(context).apply {
				setEnableDecoderFallback(true)
				setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
			}

			val player = ExoPlayer.Builder(context)
				.setRenderersFactory(renderersFactory)
				.build()
				.apply {
					volume = 0f
					repeatMode = Player.REPEAT_MODE_OFF
					playWhenReady = true
					videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
				}

			val mediaSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
				.createMediaSource(MediaItem.fromUri(Uri.parse(currentUrl)))

			player.addListener(object : Player.Listener {
				override fun onPlaybackStateChanged(playbackState: Int) {
					when (playbackState) {
						Player.STATE_READY -> {
							if (seekPositionMs > 0 && player.currentPosition < 1000) {
								player.seekTo(seekPositionMs)
							}
							isPlaying = true
							Timber.d("EpisodePreview: Playing ${item.name} from ${seekPositionMs}ms")
						}
						Player.STATE_ENDED -> {
							isPlaying = false
							Timber.d("EpisodePreview: Ended ${item.name}")
						}
						else -> { /* no-op */ }
					}
				}

				override fun onPlayerError(error: PlaybackException) {
					Timber.w("EpisodePreview: Error for ${item.name}: ${error.message}")
					isPlaying = false
				}
			})

			player.setMediaSource(mediaSource)
			player.prepare()
			exoPlayer = player

			onDispose {
				player.stop()
				player.release()
				exoPlayer = null
				isPlaying = false
			}
		}

		Box(
			modifier = modifier
				.fillMaxSize()
				.alpha(previewAlpha)
				.clip(JellyfinTheme.shapes.medium)
				.background(Color.Black)
		) {
			AndroidView(
				factory = { ctx ->
					PlayerView(ctx).apply {
						this.player = exoPlayer
						useController = false
						resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
						setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
						setKeepContentOnPlayerReset(true)
					}
				},
				update = { playerView ->
					playerView.player = exoPlayer
				},
				modifier = Modifier.fillMaxSize()
			)
		}
	}
}

/** Whether the given item type supports preview playback. */
fun isEligibleForPreview(item: BaseItemDto?): Boolean =
	item?.type == BaseItemKind.EPISODE || item?.type == BaseItemKind.MOVIE
