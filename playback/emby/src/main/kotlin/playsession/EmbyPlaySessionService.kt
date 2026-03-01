package org.moonfin.playback.emby.playsession

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jellyfin.playback.core.mediastream.mediaStream
import org.jellyfin.playback.core.model.PlayState
import org.jellyfin.playback.core.plugin.PlayerService
import org.jellyfin.playback.core.queue.queue
import timber.log.Timber

class EmbyPlaySessionService : PlayerService() {

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

	private suspend fun sendStreamStart() {
		val entry = manager.queue.entry.value ?: return
		val stream = entry.mediaStream ?: return

		Timber.d("Emby play session start: item=%s session=%s", entry, stream.identifier)
		// TODO: POST /Sessions/Playing via Emby API client
	}

	private suspend fun sendStreamUpdate() {
		val entry = manager.queue.entry.value ?: return
		entry.mediaStream ?: return

		Timber.d("Emby play session update: pos=%d paused=%s", state.positionInfo.active.inWholeMilliseconds, state.playState.value == PlayState.PAUSED)
		// TODO: POST /Sessions/Playing/Progress via Emby API client
	}

	private suspend fun sendStreamStop() {
		val entry = manager.queue.entry.value ?: return
		val stream = entry.mediaStream ?: return

		Timber.d("Emby play session stop: item=%s session=%s", entry, stream.identifier)
		// TODO: POST /Sessions/Playing/Stopped via Emby API client
	}
}
