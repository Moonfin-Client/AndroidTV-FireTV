package org.jellyfin.androidtv.ui.home.mediabar

import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import timber.log.Timber

/**
 * Composable that renders a YouTube trailer preview using ExoPlayer
 * with stream URLs resolved via NewPipe Extractor.
 */
@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerTrailerView(
	streamInfo: YouTubeStreamResolver.StreamInfo,
	startSeconds: Double,
	segments: List<SponsorBlockApi.Segment>,
	muted: Boolean = true,
	isVisible: Boolean,
	onVideoEnded: () -> Unit = {},
	onVideoReady: () -> Unit = {},
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	var player by remember { mutableStateOf<ExoPlayer?>(null) }
	val mainHandler = remember { Handler(Looper.getMainLooper()) }
	val skipRunnable = remember { mutableStateOf<Runnable?>(null) }

	val trailerAlpha by animateFloatAsState(
		targetValue = if (isVisible) 1f else 0f,
		animationSpec = tween(durationMillis = 400),
		label = "trailerAlpha",
	)

	DisposableEffect(streamInfo.videoUrl) {
		val exoPlayer = buildTrailerPlayer(
			context = context,
			streamInfo = streamInfo,
			startSeconds = startSeconds,
			muted = muted,
			onVideoReady = onVideoReady,
			onVideoEnded = onVideoEnded,
		)

		player = exoPlayer

			if (segments.isNotEmpty()) {
			val runnable = object : Runnable {
				override fun run() {
					val p = player ?: return
					if (!p.isPlaying) {
						mainHandler.postDelayed(this, 500)
						return
					}
					val currentSec = p.currentPosition / 1000.0
					for (seg in segments) {
						if (currentSec >= seg.startTime && currentSec < seg.endTime - 0.5) {
							Timber.d("ExoTrailer: Skipping SponsorBlock segment ${seg.category} at ${seg.startTime}s")
							p.seekTo((seg.endTime * 1000).toLong())
							break
						}
					}
					mainHandler.postDelayed(this, 500)
				}
			}
			skipRunnable.value = runnable
			mainHandler.postDelayed(runnable, 500)
		}

		onDispose {
			skipRunnable.value?.let { mainHandler.removeCallbacks(it) }
			skipRunnable.value = null
			exoPlayer.release()
			player = null
		}
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.alpha(trailerAlpha)
			.background(Color.Black)
	) {
		val currentPlayer = player
		if (currentPlayer != null) {
			AndroidView(
				factory = { ctx ->
					PlayerView(ctx).apply {
						this.player = currentPlayer
						useController = false
						resizeMode = if (muted) {
							AspectRatioFrameLayout.RESIZE_MODE_ZOOM
						} else {
							AspectRatioFrameLayout.RESIZE_MODE_FIT
						}
						setBackgroundColor(android.graphics.Color.BLACK)
						setShutterBackgroundColor(android.graphics.Color.BLACK)
					}
				},
				update = { view ->
					view.player = currentPlayer
				},
				modifier = Modifier.fillMaxSize()
			)
		}
	}
}

@OptIn(UnstableApi::class)
private fun buildTrailerPlayer(
	context: Context,
	streamInfo: YouTubeStreamResolver.StreamInfo,
	startSeconds: Double,
	muted: Boolean,
	onVideoReady: () -> Unit,
	onVideoEnded: () -> Unit,
): ExoPlayer {
	val dataSourceFactory = DefaultHttpDataSource.Factory()

	val player = ExoPlayer.Builder(context)
		.setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
		.build()

	player.volume = if (muted) 0f else 1f
	player.repeatMode = Player.REPEAT_MODE_OFF
	player.playWhenReady = true

	player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
		.setMaxVideoSize(1280, 720)
		.build()

	var readySignaled = false

	player.addListener(object : Player.Listener {
		override fun onPlaybackStateChanged(playbackState: Int) {
			when (playbackState) {
				Player.STATE_READY -> {
					if (!readySignaled) {
						readySignaled = true
						Timber.d("ExoTrailer: Player ready — signaling onVideoReady")
						onVideoReady()
					}
				}
				Player.STATE_ENDED -> {
					Timber.d("ExoTrailer: Playback ended")
					onVideoEnded()
				}
			}
		}

		override fun onPlayerError(error: PlaybackException) {
			Timber.w(error, "ExoTrailer: Playback error")
			onVideoEnded()
		}
	})

	if (streamInfo.isVideoOnly && streamInfo.audioUrl != null) {
		val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
			.createMediaSource(MediaItem.fromUri(streamInfo.videoUrl))
		val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
			.createMediaSource(MediaItem.fromUri(streamInfo.audioUrl))
		val merged = MergingMediaSource(videoSource, audioSource)
		player.setMediaSource(merged)
	} else {
		player.setMediaItem(MediaItem.fromUri(streamInfo.videoUrl))
	}

	player.prepare()

	if (startSeconds > 0) {
		player.seekTo((startSeconds * 1000).toLong())
	}

	return player
}
