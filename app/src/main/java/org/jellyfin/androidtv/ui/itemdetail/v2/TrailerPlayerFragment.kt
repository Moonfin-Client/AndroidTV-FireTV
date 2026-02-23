package org.jellyfin.androidtv.ui.itemdetail.v2

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.ui.home.mediabar.YouTubeStreamResolver
import org.jellyfin.androidtv.ui.home.mediabar.SponsorBlockApi
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.koin.android.ext.android.inject
import timber.log.Timber

/** Fullscreen fragment that plays a YouTube trailer via ExoPlayer with sound. */
class TrailerPlayerFragment : Fragment() {

	companion object {
		const val ARG_VIDEO_ID = "VideoId"
		const val ARG_START_SECONDS = "StartSeconds"
		const val ARG_SEGMENTS_JSON = "SegmentsJson"
	}

	private val navigationRepository: NavigationRepository by inject()
	private var player: ExoPlayer? = null
	private val mainHandler = Handler(Looper.getMainLooper())
	private var skipRunnable: Runnable? = null

	@OptIn(UnstableApi::class)
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		val videoId = requireArguments().getString(ARG_VIDEO_ID)!!
		val startSeconds = requireArguments().getDouble(ARG_START_SECONDS, 0.0)
		val segmentsJson = requireArguments().getString(ARG_SEGMENTS_JSON, "[]")

		val segments = try {
			Json.decodeFromString<List<SegmentDto>>(segmentsJson).map {
				SponsorBlockApi.Segment(it.start, it.end, it.category, it.action)
			}
		} catch (e: Exception) {
			Timber.w(e, "TrailerPlayer: Failed to parse segments JSON")
			emptyList()
		}

		val root = object : FrameLayout(requireContext()) {
			override fun dispatchKeyEvent(event: KeyEvent): Boolean {
				if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
					goBack()
					return true
				}
				return super.dispatchKeyEvent(event)
			}
		}.apply {
			setBackgroundColor(android.graphics.Color.BLACK)
			isFocusable = true
			isFocusableInTouchMode = true
		}

		val playerView = PlayerView(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT,
			)
			useController = false
			resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
			setBackgroundColor(android.graphics.Color.BLACK)
			setShutterBackgroundColor(android.graphics.Color.BLACK)
		}

		root.addView(playerView)

		lifecycleScope.launch {
			val streamInfo = withContext(Dispatchers.IO) {
				try {
					YouTubeStreamResolver.resolveStream(videoId)
				} catch (e: Exception) {
					Timber.w(e, "TrailerPlayer: Failed to resolve YouTube stream for $videoId")
					null
				}
			}

			if (streamInfo == null) {
				Timber.w("TrailerPlayer: No stream available for $videoId, going back")
				goBack()
				return@launch
			}

			if (!isAdded) return@launch

			val dataSourceFactory = DefaultHttpDataSource.Factory()

			val exoPlayer = ExoPlayer.Builder(requireContext())
				.setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
				.build()

			exoPlayer.volume = 1f
			exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
			exoPlayer.playWhenReady = true

			exoPlayer.addListener(object : Player.Listener {
				override fun onPlaybackStateChanged(playbackState: Int) {
					if (playbackState == Player.STATE_ENDED) {
						Timber.d("TrailerPlayer: Playback ended for $videoId")
						goBack()
					}
				}

				override fun onPlayerError(error: PlaybackException) {
					Timber.w(error, "TrailerPlayer: Playback error for $videoId")
					goBack()
				}
			})

			if (streamInfo.isVideoOnly && streamInfo.audioUrl != null) {
				val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
					.createMediaSource(MediaItem.fromUri(streamInfo.videoUrl))
				val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
					.createMediaSource(MediaItem.fromUri(streamInfo.audioUrl))
				exoPlayer.setMediaSource(MergingMediaSource(videoSource, audioSource))
			} else {
				exoPlayer.setMediaItem(MediaItem.fromUri(streamInfo.videoUrl))
			}

			exoPlayer.prepare()

			if (startSeconds > 0) {
				exoPlayer.seekTo((startSeconds * 1000).toLong())
			}

			player = exoPlayer
			playerView.player = exoPlayer

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
								Timber.d("TrailerPlayer: Skipping SponsorBlock segment ${seg.category} at ${seg.startTime}s")
								p.seekTo((seg.endTime * 1000).toLong())
								break
							}
						}
						mainHandler.postDelayed(this, 500)
					}
				}
				skipRunnable = runnable
				mainHandler.postDelayed(runnable, 500)
			}
		}

		return root
	}

	private fun goBack() {
		if (navigationRepository.canGoBack) {
			navigationRepository.goBack()
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		skipRunnable?.let { mainHandler.removeCallbacks(it) }
		skipRunnable = null
		player?.release()
		player = null
	}
}

@kotlinx.serialization.Serializable
private data class SegmentDto(
	val start: Double,
	val end: Double,
	val category: String = "",
	val action: String = "skip",
)
