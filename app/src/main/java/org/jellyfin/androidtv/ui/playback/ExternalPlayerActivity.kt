package org.jellyfin.androidtv.ui.playback

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.ExternalAppRepository
import org.jellyfin.androidtv.util.componentName
import org.jellyfin.androidtv.util.sdk.getDisplayName
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Activity that, once opened, opens the first item of the [VideoQueueManager.getCurrentVideoQueue] list in an external media player app.
 * Once returned it will notify the server of item completion.
 */
class ExternalPlayerActivity : FragmentActivity() {
	companion object {
		const val EXTRA_POSITION = "position"

		// https://mx.j2inter.com/api
		private const val API_MX_TITLE = "title"
		private const val API_MX_SEEK_POSITION = "position"
		private const val API_MX_FILENAME = "filename"
		private const val API_MX_SECURE_URI = "secure_uri"
		private const val API_MX_RETURN_RESULT = "return_result"
		private const val API_MX_RESULT_POSITION = "position"
		private const val API_MX_SUBS = "subs"
		private const val API_MX_SUBS_NAME = "subs.name"
		private const val API_MX_SUBS_FILENAME = "subs.filename"

		// https://wiki.videolan.org/Android_Player_Intents/
		private const val API_VLC_SUBTITLES = "subtitles_location"
		private const val API_VLC_RESULT_POSITION = "extra_position"

		// https://www.vimu.tv/player-api
		private const val API_VIMU_TITLE = "forcename"
		private const val API_VIMU_SEEK_POSITION = "startfrom"
		private const val API_VIMU_RESUME = "forceresume"
		private const val API_VIMU_RESULT_ID = "net.gtvbox.videoplayer.result"
		private const val API_VIMU_RESULT_ERROR = 4

		// The extra keys used by various video players to read the end position
		private val resultPositionExtras = arrayOf(API_MX_RESULT_POSITION, API_VLC_RESULT_POSITION)

		private const val STATE_ITEM_ID = "state_item_id"
		private const val STATE_MEDIA_SOURCE_ID = "state_media_source_id"
		private const val STATE_RUNTIME_TICKS = "state_runtime_ticks"
	}

	private val videoQueueManager by inject<VideoQueueManager>()
	private val dataRefreshService by inject<DataRefreshService>()
	private val externalAppRepository by inject<ExternalAppRepository>()
	private val api by inject<ApiClient>()

	private val playVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		Timber.i("Playback finished with result code ${result.resultCode}")
		videoQueueManager.setCurrentMediaPosition(videoQueueManager.getCurrentMediaPosition() + 1)

		if (result.isError) {
			Toast.makeText(this, R.string.video_error_unknown_error, Toast.LENGTH_LONG).show()
			finish()
		} else {
			onItemFinished(result.data)
		}
	}

	private val ActivityResult.isError get() = when (data?.action) {
		API_VIMU_RESULT_ID -> resultCode == API_VIMU_RESULT_ERROR
		else -> resultCode != RESULT_OK
	}

	private var currentItem: Pair<BaseItemDto, MediaSourceInfo>? = null

	private var savedItemId: UUID? = null
	private var savedMediaSourceId: String? = null
	private var savedRuntimeTicks: Long? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		if (savedInstanceState != null) {
			savedItemId = savedInstanceState.getString(STATE_ITEM_ID)?.toUUIDOrNull()
			savedMediaSourceId = savedInstanceState.getString(STATE_MEDIA_SOURCE_ID)
			savedRuntimeTicks = if (savedInstanceState.containsKey(STATE_RUNTIME_TICKS))
				savedInstanceState.getLong(STATE_RUNTIME_TICKS) else null
			Timber.i("Restored external player state: itemId=$savedItemId")
			return
		}

		val position = intent.getLongExtra(EXTRA_POSITION, 0).milliseconds
		playNext(position)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)

		val (item, mediaSource) = currentItem ?: return
		outState.putString(STATE_ITEM_ID, item.id.toString())
		outState.putString(STATE_MEDIA_SOURCE_ID, mediaSource.id)
		(mediaSource.runTimeTicks ?: item.runTimeTicks)?.let { ticks ->
			outState.putLong(STATE_RUNTIME_TICKS, ticks)
		}
	}

	private fun playNext(position: Duration = Duration.ZERO) {
		val currentPosition = videoQueueManager.getCurrentMediaPosition()
		val item = videoQueueManager.getCurrentVideoQueue().getOrNull(currentPosition) ?: return finish()
		val mediaSource = item.mediaSources?.firstOrNull { it.id?.toUUIDOrNull() == item.id }

		if (mediaSource == null) {
			Toast.makeText(this, R.string.msg_no_playable_items, Toast.LENGTH_LONG).show()
			finish()
		} else {
			playItem(item, mediaSource, position)
		}
	}

	private fun playItem(item: BaseItemDto, mediaSource: MediaSourceInfo, position: Duration) {
		val url = api.videosApi.getVideoStreamUrl(
			itemId = item.id,
			mediaSourceId = mediaSource.id,
			static = true,
		)

		val title = item.getDisplayName(this)
		val fileName = mediaSource.path?.let { File(it).name }
		val externalSubtitles = mediaSource.mediaStreams
			?.filter { it.type == MediaStreamType.SUBTITLE && it.isExternal }
			?.sortedWith(compareBy<MediaStream> { it.isDefault }.thenBy { it.index })
			.orEmpty()

		val subtitleUrls = externalSubtitles.map { mediaStream ->
			// We cannot use the DeliveryUrl as that is only populated when using the playback info API, which we skip as we'll always direct
			// play when using external players. We need to infer the subtitle format based on its path (similar to how the server
			// calculates it)
			val format = mediaStream.path?.substringAfterLast('.', missingDelimiterValue = mediaStream.codec.orEmpty()) ?: "srt"
			api.subtitleApi.getSubtitleUrl(
				routeItemId = item.id,
				routeMediaSourceId = mediaSource.id.toString(),
				routeIndex = mediaStream.index,
				routeFormat = format,
			)
		}.toTypedArray()
		val subtitleNames = externalSubtitles.map { it.displayTitle ?: it.title.orEmpty() }.toTypedArray()
		val subtitleLanguages = externalSubtitles.map { it.language.orEmpty() }.toTypedArray()

		Timber.i(
			"Starting item ${item.id} from $position with ${subtitleUrls.size} external subtitles: $url${
				subtitleUrls.joinToString(", ", ", ")
			}"
		)

		val playIntent = Intent(Intent.ACTION_VIEW).apply {
			val mediaType = when (item.mediaType) {
				MediaType.VIDEO -> "video/*"
				MediaType.AUDIO -> "audio/*"
				else -> null
			}

			// Set configured app to launch
			externalAppRepository
				.getCurrentExternalPlayerApp(this@ExternalPlayerActivity)
				?.componentName
				?.let(::setComponent)

			setDataAndTypeAndNormalize(url.toUri(), mediaType)

			putExtra(API_MX_SEEK_POSITION, position.inWholeMilliseconds.toInt())
			putExtra(API_MX_RETURN_RESULT, true)
			putExtra(API_MX_TITLE, title)
			putExtra(API_MX_FILENAME, fileName)
			putExtra(API_MX_SECURE_URI, true)
			putExtra(API_MX_SUBS, subtitleUrls)
			putExtra(API_MX_SUBS_NAME, subtitleNames)
			putExtra(API_MX_SUBS_FILENAME, subtitleLanguages)

			if (subtitleUrls.isNotEmpty()) putExtra(API_VLC_SUBTITLES, subtitleUrls.first().toString())

			putExtra(API_VIMU_SEEK_POSITION, position.inWholeMilliseconds.toInt())
			putExtra(API_VIMU_RESUME, false)
			putExtra(API_VIMU_TITLE, title)
		}

		try {
			currentItem = item to mediaSource
			savedItemId = item.id
			savedMediaSourceId = mediaSource.id
			savedRuntimeTicks = mediaSource.runTimeTicks ?: item.runTimeTicks
			playVideoLauncher.launch(playIntent)
		} catch (_: ActivityNotFoundException) {
			Toast.makeText(this, R.string.no_player_message, Toast.LENGTH_LONG).show()
			finish()
		}
	}


	private fun onItemFinished(result: Intent?) {
		val extras = result?.extras ?: Bundle.EMPTY

		val endPosition = resultPositionExtras.firstNotNullOfOrNull { key ->
			@Suppress("DEPRECATION") val value = extras.get(key)
			if (value is Number) value.toLong().milliseconds
			else null
		}

		val itemId = currentItem?.first?.id ?: savedItemId
		val mediaSourceId = currentItem?.second?.id ?: savedMediaSourceId
		val runtimeTicks = currentItem?.second?.runTimeTicks ?: currentItem?.first?.runTimeTicks ?: savedRuntimeTicks

		if (itemId == null || mediaSourceId == null) {
			Timber.w("Cannot report playback stop: no item data available")
			Toast.makeText(this@ExternalPlayerActivity, R.string.video_error_unknown_error, Toast.LENGTH_LONG).show()
			finish()
			return
		}

		val runtime = runtimeTicks?.ticks
		val shouldPlayNext = currentItem != null && runtime != null && endPosition != null && endPosition >= (runtime * 0.9)

		lifecycleScope.launch {
			runCatching {
				withContext(Dispatchers.IO) {
					api.playStateApi.reportPlaybackStopped(
						PlaybackStopInfo(
							itemId = itemId,
							mediaSourceId = mediaSourceId,
							positionTicks = endPosition?.inWholeTicks,
							failed = false,
						)
					)
				}
			}.onFailure { error ->
				Timber.w(error, "Failed to report playback stop event")
				Toast.makeText(this@ExternalPlayerActivity, R.string.video_error_unknown_error, Toast.LENGTH_LONG).show()
			}

			val now = Instant.now()
			dataRefreshService.lastPlayback = now
			when (currentItem?.first?.type) {
				BaseItemKind.MOVIE -> dataRefreshService.lastMoviePlayback = now
				BaseItemKind.EPISODE -> dataRefreshService.lastTvPlayback = now
				else -> Unit
			}

			if (shouldPlayNext) playNext()
			else finish()
		}
	}
}
