package org.jellyfin.androidtv.ui.playback

import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

class VideoQueueManager {
	private var _currentVideoQueue: List<BaseItemDto> = emptyList()
	private var _currentMediaPosition = -1
	private var _lastPlayedAudioLanguageIsoCode: String? = null
	private var _serverIds: List<UUID?> = emptyList()

	fun setCurrentVideoQueue(items: List<BaseItemDto>?, serverIds: List<UUID?>? = null) {
		if (items.isNullOrEmpty()) return clearVideoQueue()

		_currentVideoQueue = items.toMutableList()
		_currentMediaPosition = 0
		_serverIds = serverIds?.take(items.size) ?: List(items.size) { null }
	}

	fun getCurrentVideoQueue(): List<BaseItemDto> = _currentVideoQueue
	
	fun getServerIdForPosition(position: Int): UUID? {
		return _serverIds.getOrNull(position)
	}

	fun setCurrentMediaPosition(currentMediaPosition: Int) {
		if (currentMediaPosition !in 0.._currentVideoQueue.size) return

		_currentMediaPosition = currentMediaPosition
	}

	fun getCurrentMediaPosition() = _currentMediaPosition

	fun getLastPlayedAudioLanguageIsoCode(): String? {
		return _lastPlayedAudioLanguageIsoCode
	}

	fun setLastPlayedAudioLanguageIsoCode(isoCode: String) {
		_lastPlayedAudioLanguageIsoCode = isoCode
	}

	fun clearVideoQueue() {
		_currentVideoQueue = emptyList()
		_currentMediaPosition = -1
		_lastPlayedAudioLanguageIsoCode = null
		_serverIds = emptyList()
	}
}
