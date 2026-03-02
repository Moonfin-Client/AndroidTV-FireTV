package org.jellyfin.androidtv.data.eventhandling

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.syncplay.SyncPlayManager
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.playback.PlaybackControllerContainer
import org.jellyfin.androidtv.ui.playback.setSubtitleIndex
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.api.sockets.subscribeGeneralCommand
import org.jellyfin.sdk.api.sockets.subscribeGeneralCommands
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.LibraryUpdateInfo
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMessage
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.SyncPlayCommandMessage
import org.jellyfin.sdk.model.api.SyncPlayGroupUpdateMessage
import org.jellyfin.sdk.model.extensions.get
import org.jellyfin.sdk.model.extensions.getValue
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.moonfin.server.core.model.ServerType
import org.moonfin.server.core.model.ServerWebSocketMessage
import org.moonfin.server.emby.socket.EmbyWebSocketClient
import timber.log.Timber
import java.time.Instant
import java.util.UUID

class SocketHandler(
	private val context: Context,
	private val api: ApiClient,
	private val dataRefreshService: DataRefreshService,
	private val mediaManager: MediaManager,
	private val playbackControllerContainer: PlaybackControllerContainer,
	private val navigationRepository: NavigationRepository,
	private val audioManager: AudioManager,
	private val itemLauncher: ItemLauncher,
	private val playbackHelper: PlaybackHelper,
	private val syncPlayManager: SyncPlayManager,
	private val lifecycle: Lifecycle,
	private val embyWebSocketClient: EmbyWebSocketClient,
	private val serverRepository: ServerRepository,
) {
	private val activeServerType: ServerType
		get() = serverRepository.currentServer.value?.serverType ?: ServerType.JELLYFIN

	init {
		lifecycle.coroutineScope.launch(Dispatchers.IO) {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				var subscriptionJob: Job? = null
				serverRepository.currentServer
					.onEach { server ->
						subscriptionJob?.cancel()
						embyWebSocketClient.disconnect()
						val type = server?.serverType ?: return@onEach
						subscriptionJob = launch {
							when (type) {
								ServerType.JELLYFIN -> subscribeJellyfin(this)
								ServerType.EMBY -> subscribeEmby(this)
							}
						}
					}
					.launchIn(this)
			}
		}
	}

	suspend fun updateSession() {
		if (activeServerType == ServerType.JELLYFIN) updateJellyfinSession()
	}

	private suspend fun updateJellyfinSession() {
		try {
			withContext(Dispatchers.IO) {
				api.sessionApi.postCapabilities(
					playableMediaTypes = listOf(MediaType.VIDEO, MediaType.AUDIO),
					supportsMediaControl = true,
					supportedCommands = buildList {
						add(GeneralCommandType.DISPLAY_CONTENT)
						add(GeneralCommandType.SET_SUBTITLE_STREAM_INDEX)
						add(GeneralCommandType.SET_AUDIO_STREAM_INDEX)

						add(GeneralCommandType.DISPLAY_MESSAGE)
						add(GeneralCommandType.SEND_STRING)

						if (!audioManager.isVolumeFixed) {
							add(GeneralCommandType.VOLUME_UP)
							add(GeneralCommandType.VOLUME_DOWN)
							add(GeneralCommandType.SET_VOLUME)

							add(GeneralCommandType.MUTE)
							add(GeneralCommandType.UNMUTE)
							add(GeneralCommandType.TOGGLE_MUTE)
						}
					},
				)
			}
		} catch (err: ApiClientException) {
			Timber.e(err, "Unable to update capabilities")
		}
	}

	private fun subscribeJellyfin(coroutineScope: CoroutineScope) = api.webSocket.apply {
		subscribe<LibraryChangedMessage>()
			.onEach { message -> message.data?.let(::onLibraryChanged) }
			.launchIn(coroutineScope)

		subscribe<PlayMessage>()
			.onEach { message -> onPlayMessage(message) }
			.launchIn(coroutineScope)

		subscribe<PlaystateMessage>()
			.onEach { message -> onPlayStateMessage(message) }
			.launchIn(coroutineScope)

		subscribeGeneralCommand(GeneralCommandType.SET_SUBTITLE_STREAM_INDEX)
			.onEach { message ->
				val index = message["index"]?.toIntOrNull() ?: return@onEach

				withContext(Dispatchers.Main) {
					playbackControllerContainer.playbackController?.setSubtitleIndex(index)
				}
			}
			.launchIn(coroutineScope)

		subscribeGeneralCommand(GeneralCommandType.SET_AUDIO_STREAM_INDEX)
			.onEach { message ->
				val index = message["index"]?.toIntOrNull() ?: return@onEach

				withContext(Dispatchers.Main) {
					playbackControllerContainer.playbackController?.switchAudioStream(index)
				}
			}
			.launchIn(coroutineScope)

		subscribeGeneralCommand(GeneralCommandType.DISPLAY_CONTENT)
			.onEach { message ->
				val itemId by message
				val itemType by message

				val itemUuid = itemId?.toUUIDOrNull()
				val itemKind = itemType?.let { type ->
					BaseItemKind.entries.find { value ->
						value.serialName.equals(type, true)
					}
				}

				if (itemUuid != null && itemKind != null) onDisplayContent(itemUuid, itemKind)
			}
			.launchIn(coroutineScope)

		subscribeGeneralCommands(setOf(GeneralCommandType.DISPLAY_MESSAGE, GeneralCommandType.SEND_STRING))
			.onEach { message ->
				val header by message
				val text by message
				val string by message

				onDisplayMessage(header, text ?: string)
			}
			.launchIn(coroutineScope)

		subscribe<SyncPlayCommandMessage>()
			.onEach { message -> onSyncPlayCommand(message) }
			.launchIn(coroutineScope)

		subscribe<SyncPlayGroupUpdateMessage>()
			.onEach { message -> onSyncPlayGroupUpdate(message) }
			.launchIn(coroutineScope)
	}

	private suspend fun subscribeEmby(coroutineScope: CoroutineScope) {
		embyWebSocketClient.connect()

		embyWebSocketClient.messages
			.onEach { message -> handleEmbyMessage(message) }
			.launchIn(coroutineScope)
	}

	private suspend fun handleEmbyMessage(message: ServerWebSocketMessage) {
		when (message) {
			is ServerWebSocketMessage.LibraryChanged -> {
				Timber.d("Emby library changed: +${message.itemsAdded.size} ~${message.itemsUpdated.size} -${message.itemsRemoved.size}")
				if (message.itemsAdded.isNotEmpty() || message.itemsRemoved.isNotEmpty()) {
					dataRefreshService.lastLibraryChange = Instant.now()
				}
			}

			is ServerWebSocketMessage.UserDataChanged -> {
				Timber.d("Emby user data changed for ${message.itemIds.size} items")
				dataRefreshService.lastLibraryChange = Instant.now()
			}

			is ServerWebSocketMessage.Play -> {
				val uuids = message.itemIds.mapNotNull { it.toUUIDOrNull() }
				if (uuids.isEmpty()) return
				runCatching {
					playbackHelper.retrieveAndPlay(
						uuids,
						false,
						message.startPositionTicks,
						null,
						context,
					)
				}.onFailure { Timber.w(it, "Failed to start Emby remote playback") }
			}

			is ServerWebSocketMessage.Playstate -> withContext(Dispatchers.Main) {
				if (mediaManager.hasAudioQueueItems()) return@withContext

				val controller = playbackControllerContainer.playbackController
				when (message.command) {
					"Stop" -> controller?.endPlayback(true)
					"Pause", "Unpause", "PlayPause" -> controller?.playPause()
					"NextTrack" -> controller?.next()
					"PreviousTrack" -> controller?.prev()
					"Seek" -> controller?.seek(
						(message.seekPositionTicks ?: 0) / 10_000
					)
					"Rewind" -> controller?.rewind()
					"FastForward" -> controller?.fastForward()
				}
			}

			is ServerWebSocketMessage.GeneralCommand -> handleEmbyGeneralCommand(message)

			is ServerWebSocketMessage.ServerRestarting -> {
				Timber.i("Emby server restarting")
				onDisplayMessage(null, "Server is restarting...")
			}

			is ServerWebSocketMessage.ServerShuttingDown -> {
				Timber.i("Emby server shutting down")
				onDisplayMessage(null, "Server is shutting down...")
			}

			is ServerWebSocketMessage.SessionEnded -> {
				Timber.i("Emby session ended: ${message.sessionId}")
			}

			is ServerWebSocketMessage.ScheduledTaskEnded -> {
				Timber.d("Emby task ended: ${message.taskName} (${message.status})")
			}
		}
	}

	private suspend fun handleEmbyGeneralCommand(command: ServerWebSocketMessage.GeneralCommand) {
		when (command.name) {
			"DisplayContent" -> {
				val itemId = command.arguments["ItemId"]?.toUUIDOrNull() ?: return
				val itemType = command.arguments["ItemType"]?.let { type ->
					BaseItemKind.entries.find { it.serialName.equals(type, true) }
				}
				if (itemType != null) onDisplayContent(itemId, itemType)
			}

			"DisplayMessage", "SendString" -> {
				onDisplayMessage(
					command.arguments["Header"],
					command.arguments["Text"] ?: command.arguments["String"],
				)
			}

			"SetSubtitleStreamIndex" -> {
				val index = command.arguments["Index"]?.toIntOrNull() ?: return
				withContext(Dispatchers.Main) {
					playbackControllerContainer.playbackController?.setSubtitleIndex(index)
				}
			}

			"SetAudioStreamIndex" -> {
				val index = command.arguments["Index"]?.toIntOrNull() ?: return
				withContext(Dispatchers.Main) {
					playbackControllerContainer.playbackController?.switchAudioStream(index)
				}
			}
		}
	}

	private fun onLibraryChanged(info: LibraryUpdateInfo) {
		Timber.d(buildString {
			appendLine("Library changed.")
			appendLine("Added ${info.itemsAdded.size} items")
			appendLine("Removed ${info.itemsRemoved.size} items")
			appendLine("Updated ${info.itemsUpdated.size} items")
		})

		if (info.itemsAdded.any() || info.itemsRemoved.any())
			dataRefreshService.lastLibraryChange = Instant.now()
	}

	private fun onPlayMessage(message: PlayMessage) {
		val itemIds = message.data?.itemIds ?: return

		runCatching {
			playbackHelper.retrieveAndPlay(
				itemIds,
				false,
				message.data?.startPositionTicks,
				message.data?.startIndex,
				context
			)
		}.onFailure { Timber.w(it, "Failed to start playback") }
	}

	@Suppress("ComplexMethod")
	private suspend fun onPlayStateMessage(message: PlaystateMessage) = withContext(Dispatchers.Main) {
		Timber.i("Received PlayStateMessage with command ${message.data?.command}")

		when {
			mediaManager.hasAudioQueueItems() -> {
				Timber.i("Ignoring PlayStateMessage: should be handled by PlaySessionSocketService")
				return@withContext
			}

			else -> {
				val playbackController = playbackControllerContainer.playbackController
				when (message.data?.command) {
					PlaystateCommand.STOP -> playbackController?.endPlayback(true)
					PlaystateCommand.PAUSE, PlaystateCommand.UNPAUSE, PlaystateCommand.PLAY_PAUSE -> playbackController?.playPause()
					PlaystateCommand.NEXT_TRACK -> playbackController?.next()
					PlaystateCommand.PREVIOUS_TRACK -> playbackController?.prev()
					PlaystateCommand.SEEK -> playbackController?.seek(
						org.jellyfin.androidtv.data.syncplay.SyncPlayUtils.ticksToMs(message.data?.seekPositionTicks ?: 0)
					)

					PlaystateCommand.REWIND -> playbackController?.rewind()
					PlaystateCommand.FAST_FORWARD -> playbackController?.fastForward()

					null -> Unit
				}
			}
		}
	}

	private suspend fun onDisplayContent(itemId: UUID, itemKind: BaseItemKind) = withContext(Dispatchers.Main) {
		val playbackController = playbackControllerContainer.playbackController

		if (playbackController?.isPlaying == true || playbackController?.isPaused == true) {
			Timber.i("Not launching $itemId: playback in progress")
			return@withContext
		}

		Timber.i("Launching $itemId")

		when (itemKind) {
			BaseItemKind.USER_VIEW,
			BaseItemKind.COLLECTION_FOLDER -> {
				val item by api.userLibraryApi.getItem(itemId = itemId)
				itemLauncher.launchUserView(item)
			}

			else -> navigationRepository.navigate(Destinations.itemDetails(itemId))
		}
	}

	private fun onDisplayMessage(header: String?, text: String?) {
		val toastMessage = buildString {
			if (!header.isNullOrBlank()) append(header, ": ")
			append(text)
		}

		lifecycle.coroutineScope.launch(Dispatchers.Main) {
			Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
		}
	}

	private fun onSyncPlayCommand(message: SyncPlayCommandMessage) {
		val data = message.data ?: return
		syncPlayManager.onPlaybackCommand(data)
	}

	private fun onSyncPlayGroupUpdate(message: SyncPlayGroupUpdateMessage) {
		val data = message.data ?: return
		syncPlayManager.onGroupUpdate(data)
	}
}
