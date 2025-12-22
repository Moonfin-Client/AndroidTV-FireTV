package org.jellyfin.playback.jellyfin.playsession

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.playback.core.mediastream.MediaConversionMethod
import org.jellyfin.playback.core.mediastream.mediaStream
import org.jellyfin.playback.core.model.PlayState
import org.jellyfin.playback.core.model.RepeatMode
import org.jellyfin.playback.core.plugin.PlayerService
import org.jellyfin.playback.core.queue.queue
import org.jellyfin.playback.jellyfin.queue.baseItem
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.QueueItem
import org.jellyfin.sdk.model.extensions.inWholeTicks
import timber.log.Timber
import java.util.UUID
import kotlin.math.roundToInt
import org.jellyfin.sdk.model.api.RepeatMode as SdkRepeatMode

class PlaySessionService(
	private val api: ApiClient,
	private val apiClientResolver: ((UUID?) -> ApiClient?)? = null,
) : PlayerService() {
	override suspend fun onInitialize() {
		state.playState.onEach { playState ->
			when (playState) {
				PlayState.PLAYING -> sendStreamStart()
				PlayState.STOPPED -> sendStreamStop()
				PlayState.PAUSED -> sendStreamUpdate()
				PlayState.ERROR -> sendStreamStop()
			}
		}.launchIn(coroutineScope)
	}

	private val MediaConversionMethod.playMethod
		get() = when (this) {
			MediaConversionMethod.None -> PlayMethod.DIRECT_PLAY
			MediaConversionMethod.Remux -> PlayMethod.DIRECT_STREAM
			MediaConversionMethod.Transcode -> PlayMethod.TRANSCODE
		}

	private val RepeatMode.remoteRepeatMode
		get() = when (this) {
			RepeatMode.NONE -> SdkRepeatMode.REPEAT_NONE
			RepeatMode.REPEAT_ENTRY_ONCE -> SdkRepeatMode.REPEAT_ONE
			RepeatMode.REPEAT_ENTRY_INFINITE -> SdkRepeatMode.REPEAT_ALL
		}

	/**
	 * Get the correct API client for the given item.
	 * If the item has a serverId and we have an apiClientResolver, use the item's server API client.
	 * Otherwise, fall back to the default API client.
	 */
	private fun getApiClientForItem(item: BaseItemDto): ApiClient {
		val serverId = item.serverId
		if (serverId.isNullOrEmpty() || apiClientResolver == null) return api
		
		val serverUuid = parseServerId(serverId) ?: return api
		return apiClientResolver.invoke(serverUuid) ?: api
	}

	/**
	 * Parse a serverId string to UUID, handling the case where hyphens may be missing.
	 */
	private fun parseServerId(serverId: String): UUID? {
		// Try parsing directly first
		try {
			return UUID.fromString(serverId)
		} catch (_: IllegalArgumentException) {}
		
		// If 32 chars without hyphens, add them back (8-4-4-4-12 format)
		if (serverId.length == 32 && !serverId.contains("-")) {
			val normalized = "${serverId.substring(0, 8)}-${serverId.substring(8, 12)}-${serverId.substring(12, 16)}-${serverId.substring(16, 20)}-${serverId.substring(20)}"
			try {
				return UUID.fromString(normalized)
			} catch (_: IllegalArgumentException) {}
		}
		return null
	}

	suspend fun sendUpdateIfActive() {
		coroutineScope.launch { sendStreamUpdate() }
	}

	private suspend fun getQueue(): List<QueueItem> {
		// The queues are lazy loaded so we only load a small amount of items to set as queue on the
		// backend.
		return manager.queue
			.peekNext(15)
			.mapNotNull { it.baseItem }
			.map { QueueItem(id = it.id, playlistItemId = it.playlistItemId) }
	}

	private suspend fun sendStreamStart() {
		val entry = manager.queue.entry.value ?: return
		val stream = entry.mediaStream ?: return
		val item = entry.baseItem ?: return
		val itemApi = getApiClientForItem(item)

		runCatching {
			itemApi.playStateApi.reportPlaybackStart(
				PlaybackStartInfo(
					itemId = item.id,
					playSessionId = stream.identifier,
					playlistItemId = item.playlistItemId,
					canSeek = true,
					isMuted = state.volume.muted,
					volumeLevel = (state.volume.volume * 100).roundToInt(),
					isPaused = state.playState.value != PlayState.PLAYING,
					aspectRatio = state.videoSize.value.aspectRatio.toString(),
					positionTicks = withContext(Dispatchers.Main) { state.positionInfo.active.inWholeTicks },
					playMethod = stream.conversionMethod.playMethod,
					repeatMode = state.repeatMode.value.remoteRepeatMode,
					nowPlayingQueue = getQueue(),
					playbackOrder = when (state.playbackOrder.value) {
						org.jellyfin.playback.core.model.PlaybackOrder.DEFAULT -> PlaybackOrder.DEFAULT
						org.jellyfin.playback.core.model.PlaybackOrder.RANDOM -> PlaybackOrder.SHUFFLE
						org.jellyfin.playback.core.model.PlaybackOrder.SHUFFLE -> PlaybackOrder.SHUFFLE
					}
				)
			)
		}.onFailure { error -> Timber.w(error, "Failed to send playback start event") }
	}

	private suspend fun sendStreamUpdate() {
		val entry = manager.queue.entry.value ?: return
		val stream = entry.mediaStream ?: return
		val item = entry.baseItem ?: return
		val itemApi = getApiClientForItem(item)

		runCatching {
			itemApi.playStateApi.reportPlaybackProgress(
				PlaybackProgressInfo(
					itemId = item.id,
					playSessionId = stream.identifier,
					playlistItemId = item.playlistItemId,
					canSeek = true,
					isMuted = state.volume.muted,
					volumeLevel = (state.volume.volume * 100).roundToInt(),
					isPaused = state.playState.value != PlayState.PLAYING,
					aspectRatio = state.videoSize.value.aspectRatio.toString(),
					positionTicks = withContext(Dispatchers.Main) { state.positionInfo.active.inWholeTicks },
					playMethod = stream.conversionMethod.playMethod,
					repeatMode = state.repeatMode.value.remoteRepeatMode,
					nowPlayingQueue = getQueue(),
					playbackOrder = when (state.playbackOrder.value) {
						org.jellyfin.playback.core.model.PlaybackOrder.DEFAULT -> PlaybackOrder.DEFAULT
						org.jellyfin.playback.core.model.PlaybackOrder.RANDOM -> PlaybackOrder.SHUFFLE
						org.jellyfin.playback.core.model.PlaybackOrder.SHUFFLE -> PlaybackOrder.SHUFFLE
					}
				)
			)
		}.onFailure { error -> Timber.w("Failed to send playback update event", error) }
	}

	private suspend fun sendStreamStop() {
		val entry = manager.queue.entry.value ?: return
		val stream = entry.mediaStream ?: return
		val item = entry.baseItem ?: return
		val itemApi = getApiClientForItem(item)

		runCatching {
			itemApi.playStateApi.reportPlaybackStopped(
				PlaybackStopInfo(
					itemId = item.id,
					playSessionId = stream.identifier,
					playlistItemId = item.playlistItemId,
					positionTicks = withContext(Dispatchers.Main) { state.positionInfo.active.inWholeTicks },
					failed = false,
					nowPlayingQueue = getQueue(),
				)
			)
		}.onFailure { error -> Timber.w("Failed to send playback stop event", error) }
	}
}
