package org.jellyfin.androidtv.ui.composable.item

import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.playback.segment.MediaSegmentRepository
import org.jellyfin.androidtv.util.UUIDUtils
import org.jellyfin.androidtv.util.sdk.ApiClientFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.koin.compose.koinInject
import timber.log.Timber

/** Delay before starting preview playback to debounce quick scrolling. */
private const val PREVIEW_START_DELAY_MS = 500L

private const val MAX_PREVIEW_DURATION_MS = 30_000L

/** Seek to 20% of runtime as a fallback when no intro segment or resume position is available. */
private fun runtimeFallbackMs(item: BaseItemDto): Long {
	val runtimeTicks = item.runTimeTicks ?: 0L
	return if (runtimeTicks > 0) (runtimeTicks / 10_000L) / 5 else 0L
}

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
	muted: Boolean = true,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val api = koinInject<ApiClient>()
	val apiClientFactory = koinInject<ApiClientFactory>()
	val userRepository = koinInject<UserRepository>()
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

		delay(PREVIEW_START_DELAY_MS)

		val userId = userRepository.currentUser.value?.id ?: return@LaunchedEffect
		try {
			val (primaryUrl, introEndMs) = withContext(Dispatchers.IO) {
				// get users preferred audio language
				val preferredLanguage = userRepository.currentUser.value?.configuration?.audioLanguagePreference
				val itemDetails = effectiveApi.userLibraryApi.getItem(item.id, userId).content
				// jellyfin uses 3 letter country codes here, no need to convert to 2 letter
				val audioStreams: List<MediaStream>? = itemDetails.mediaSources?.firstOrNull()?.mediaStreams?.filter { it.type == MediaStreamType.AUDIO }
				val match = audioStreams?.find { it.language?.equals(preferredLanguage, ignoreCase = true) == true }
				// use matched language index, or try first audio track, null otherwise
				val audioIndex = match?.index ?: audioStreams?.firstOrNull()?.index
				Timber.d("EpisodePreviewOverlay: ${item.name}, audio lang: ${if (match != null) "${match.language}" else if (audioIndex != null) "Default" else "None"}, index: $audioIndex")

				// Transcoded stream first: forces H.264 8-bit (SDR), no Dolby Vision/HDR
				val transcoded = effectiveApi.videosApi.getVideoStreamUrl(
					itemId = item.id,
					static = false,
					videoCodec = "h264",
					maxVideoBitDepth = 8,
					videoBitRate = 1_000_000, // 1 Mbps, force a bitrate or jellyfin will try something stupid
					audioCodec = "aac",
					audioStreamIndex = audioIndex,
					audioBitRate = 128_000,
					audioChannels = 2,
					subtitleMethod = org.jellyfin.sdk.model.api.SubtitleDeliveryMethod.DROP,
				)

				val seekMs = try {
					val positionTicks = item.userData?.playbackPositionTicks ?: 0L
					if (positionTicks > 0) {
						positionTicks / 10_000L
					} else {
						val segments = mediaSegmentRepository.getSegmentsForItem(item)
						val introSegment = segments.firstOrNull { it.type == MediaSegmentType.INTRO }
						introSegment?.endTicks?.let { it / 10_000L }
							?: runtimeFallbackMs(item)
					}
				} catch (e: Exception) {
					Timber.w(e, "EpisodePreview: Failed to get intro segments for ${item.name}")
					val positionTicks = item.userData?.playbackPositionTicks ?: 0L
					if (positionTicks > 0) positionTicks / 10_000L else runtimeFallbackMs(item)
				}

				Pair(transcoded, seekMs)
			}

			streamUrl = primaryUrl
			seekPositionMs = introEndMs
		} catch (e: Exception) {
			Timber.w(e, "EpisodePreview: Failed to resolve stream URL for ${item.name}")
		}
	}

	if (streamUrl != null && focused) {
		val currentUrl = streamUrl!!

		DisposableEffect(currentUrl) {
			val renderersFactory = DefaultRenderersFactory(context).apply {
				setEnableDecoderFallback(true)
				setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
			}

			val trackSelector = DefaultTrackSelector(context).apply {
				parameters = parameters.buildUpon()
					.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
					.build()
			}

			val player = ExoPlayer.Builder(context)
				.setRenderersFactory(renderersFactory)
				.setTrackSelector(trackSelector)
				.build()
				.apply {
					volume = if (muted) 0f else 1f
					repeatMode = Player.REPEAT_MODE_OFF
					playWhenReady = true
					videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
				}

			val mediaSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
				.createMediaSource(MediaItem.fromUri(Uri.parse(currentUrl)))

			var hasSeeked = false
			val handler = Handler(Looper.getMainLooper())
			val stopRunnable = Runnable {
				isPlaying = false
				player.stop()
			}

			player.addListener(object : Player.Listener {
				override fun onPlaybackStateChanged(playbackState: Int) {
					when (playbackState) {
						Player.STATE_READY -> {
							if (!hasSeeked && seekPositionMs > 0) {
								hasSeeked = true
								player.seekTo(seekPositionMs)
							}
							isPlaying = true
							handler.removeCallbacks(stopRunnable)
							handler.postDelayed(stopRunnable, MAX_PREVIEW_DURATION_MS)
						}
						Player.STATE_ENDED -> {
							isPlaying = false
							handler.removeCallbacks(stopRunnable)
						}
						else -> { /* no-op */ }
					}
				}

				override fun onPlayerError(error: PlaybackException) {
					Timber.w("EpisodePreview: Error for ${item.name}: ${error.message}")
					isPlaying = false
					handler.removeCallbacks(stopRunnable)
				}
			})

			player.setMediaSource(mediaSource)
			player.prepare()
			exoPlayer = player

			onDispose {
				handler.removeCallbacks(stopRunnable)
				player.stop()
				player.release()
				exoPlayer = null
				isPlaying = false
			}
		}

		val lifecycleOwner = LocalLifecycleOwner.current
		DisposableEffect(lifecycleOwner, exoPlayer) {
			val observer = LifecycleEventObserver { _, event ->
				when (event) {
					Lifecycle.Event.ON_PAUSE,
					Lifecycle.Event.ON_STOP,
					Lifecycle.Event.ON_DESTROY -> {
						exoPlayer?.stop()
						exoPlayer?.release()
						exoPlayer = null
						isPlaying = false
					}
					else -> {}
				}
			}
			lifecycleOwner.lifecycle.addObserver(observer)
			if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
				exoPlayer?.stop()
				exoPlayer?.release()
				exoPlayer = null
				isPlaying = false
			}
			onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
	item?.type == BaseItemKind.EPISODE || item?.type == BaseItemKind.MOVIE || item?.type == BaseItemKind.MUSIC_VIDEO || item?.type == BaseItemKind.VIDEO
