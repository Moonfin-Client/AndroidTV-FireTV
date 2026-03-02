package org.moonfin.server.emby.socket

import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.moonfin.server.core.api.ServerWebSocketApi
import org.moonfin.server.core.model.ServerWebSocketMessage
import org.moonfin.server.emby.EmbyApiClient
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.min

class EmbyWebSocketClient(
	private val api: EmbyApiClient,
	private val audioManager: AudioManager,
) : ServerWebSocketApi {

	private val json = Json { ignoreUnknownKeys = true }
	private val _messages = MutableSharedFlow<ServerWebSocketMessage>(extraBufferCapacity = 64)
	override val messages: Flow<ServerWebSocketMessage> = _messages

	private var webSocket: WebSocket? = null
	private var httpClient: OkHttpClient? = null
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var reconnectAttempt = 0
	private var reconnectJob: Job? = null
	private var keepAliveJob: Job? = null

	override suspend fun connect() {
		disconnect()
		if (!api.isConfigured) return
		val token = api.accessToken ?: return

		val wsUrl = buildWsUrl(api.baseUrl, token, api.deviceId)

		withContext(Dispatchers.IO) {
			val client = OkHttpClient.Builder()
				.pingInterval(30, TimeUnit.SECONDS)
				.build()
			httpClient = client

			val request = Request.Builder().url(wsUrl).build()
			webSocket = client.newWebSocket(request, createListener())
		}
	}

	override suspend fun disconnect() {
		keepAliveJob?.cancel()
		keepAliveJob = null
		reconnectJob?.cancel()
		reconnectJob = null
		reconnectAttempt = 0
		webSocket?.close(1000, null)
		webSocket = null
		httpClient?.dispatcher?.executorService?.shutdown()
		httpClient = null
	}

	fun forceDisconnect() {
		scope.launch { disconnect() }
	}

	private fun buildWsUrl(baseUrl: String, token: String, deviceId: String): String {
		val wsBase = baseUrl.trimEnd('/')
			.replace("http://", "ws://")
			.replace("https://", "wss://")
		return "$wsBase/embywebsocket?api_key=$token&deviceId=${URLEncoder.encode(deviceId, "UTF-8")}"
	}

	private suspend fun postCapabilities() {
		try {
			val commands = buildList {
				add("DisplayContent")
				add("SetSubtitleStreamIndex")
				add("SetAudioStreamIndex")
				add("DisplayMessage")
				add("SendString")
				if (!audioManager.isVolumeFixed) {
					add("VolumeUp")
					add("VolumeDown")
					add("SetVolume")
					add("Mute")
					add("Unmute")
					add("ToggleMute")
				}
			}
			api.postCapabilities(
				playableMediaTypes = "Video,Audio",
				supportedCommands = commands.joinToString(","),
				supportsMediaControl = true,
			)
			Timber.i("Emby session capabilities posted")
		} catch (e: Exception) {
			Timber.e(e, "Failed to post Emby session capabilities")
		}
	}

	private fun createListener() = object : WebSocketListener() {
		override fun onOpen(webSocket: WebSocket, response: Response) {
			Timber.i("Emby WebSocket connected")
			reconnectAttempt = 0
			scope.launch { postCapabilities() }
		}

		override fun onMessage(webSocket: WebSocket, text: String) {
			handleRawMessage(webSocket, text)
		}

		override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
			webSocket.close(code, reason)
		}

		override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
			Timber.w(t, "Emby WebSocket failure")
			keepAliveJob?.cancel()
			scheduleReconnect()
		}

		override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
			Timber.i("Emby WebSocket closed: %d %s", code, reason)
			keepAliveJob?.cancel()
			if (code != 1000) scheduleReconnect()
		}
	}

	private fun handleRawMessage(ws: WebSocket, text: String) {
		try {
			val root = json.parseToJsonElement(text).jsonObject
			val messageType = root["MessageType"]?.jsonPrimitive?.contentOrNull ?: return
			val data = root["Data"]

			if (messageType == "ForceKeepAlive") {
				val intervalSeconds = data?.jsonPrimitive?.longOrNull ?: 60
				startKeepAlive(ws, intervalSeconds)
				return
			}

			val dataObj = data as? JsonObject
			parseMessage(messageType, dataObj)?.let { msg ->
				scope.launch { _messages.emit(msg) }
			}
		} catch (e: Exception) {
			Timber.w(e, "Failed to parse WebSocket message")
		}
	}

	private fun startKeepAlive(ws: WebSocket, intervalSeconds: Long) {
		keepAliveJob?.cancel()
		keepAliveJob = scope.launch {
			while (isActive) {
				delay(intervalSeconds * 1000 / 2)
				ws.send("{\"MessageType\":\"KeepAlive\"}")
			}
		}
	}

	private fun scheduleReconnect() {
		if (!api.isConfigured) return
		reconnectJob?.cancel()
		reconnectJob = scope.launch {
			val delayMs = min(30_000L, 1_000L * (1L shl min(reconnectAttempt, 5)))
			reconnectAttempt++
			Timber.i("Emby WebSocket reconnecting in %d ms (attempt %d)", delayMs, reconnectAttempt)
			delay(delayMs)
			connect()
		}
	}

	private fun parseMessage(type: String, data: JsonObject?): ServerWebSocketMessage? = when (type) {
		"LibraryChanged" -> parseLibraryChanged(data)
		"UserDataChanged" -> parseUserDataChanged(data)
		"Play" -> parsePlay(data)
		"Playstate" -> parsePlaystate(data)
		"GeneralCommand" -> parseGeneralCommand(data)
		"ServerRestarting" -> ServerWebSocketMessage.ServerRestarting
		"ServerShuttingDown" -> ServerWebSocketMessage.ServerShuttingDown
		"SessionEnded" -> parseSessionEnded(data)
		"ScheduledTaskEnded" -> parseScheduledTaskEnded(data)
		else -> {
			Timber.d("Unhandled Emby WebSocket message: %s", type)
			null
		}
	}

	private fun parseLibraryChanged(data: JsonObject?): ServerWebSocketMessage.LibraryChanged? {
		data ?: return null
		return ServerWebSocketMessage.LibraryChanged(
			itemsAdded = data.stringList("ItemsAdded"),
			itemsUpdated = data.stringList("ItemsUpdated"),
			itemsRemoved = data.stringList("ItemsRemoved"),
		)
	}

	private fun parseUserDataChanged(data: JsonObject?): ServerWebSocketMessage.UserDataChanged? {
		data ?: return null
		val userId = data["UserId"]?.jsonPrimitive?.contentOrNull ?: return null
		val itemIds = data["UserDataList"]?.jsonArray
			?.mapNotNull { it.jsonObject["ItemId"]?.jsonPrimitive?.contentOrNull }
			?: emptyList()
		return ServerWebSocketMessage.UserDataChanged(userId = userId, itemIds = itemIds)
	}

	private fun parsePlay(data: JsonObject?): ServerWebSocketMessage.Play? {
		data ?: return null
		val itemIds = data.stringList("ItemIds")
		if (itemIds.isEmpty()) return null
		return ServerWebSocketMessage.Play(
			itemIds = itemIds,
			startPositionTicks = data["StartPositionTicks"]?.jsonPrimitive?.longOrNull,
			playCommand = data["PlayCommand"]?.jsonPrimitive?.contentOrNull ?: "PlayNow",
		)
	}

	private fun parsePlaystate(data: JsonObject?): ServerWebSocketMessage.Playstate? {
		data ?: return null
		val command = data["Command"]?.jsonPrimitive?.contentOrNull ?: return null
		return ServerWebSocketMessage.Playstate(
			command = command,
			seekPositionTicks = data["SeekPositionTicks"]?.jsonPrimitive?.longOrNull,
		)
	}

	private fun parseGeneralCommand(data: JsonObject?): ServerWebSocketMessage.GeneralCommand? {
		data ?: return null
		val name = data["Name"]?.jsonPrimitive?.contentOrNull ?: return null
		val arguments = data["Arguments"]?.jsonObject
			?.mapValues { (_, v) -> v.jsonPrimitive.contentOrNull ?: "" }
			?: emptyMap()
		return ServerWebSocketMessage.GeneralCommand(name = name, arguments = arguments)
	}

	private fun parseSessionEnded(data: JsonObject?): ServerWebSocketMessage.SessionEnded? {
		data ?: return null
		val sessionId = data["Id"]?.jsonPrimitive?.contentOrNull
			?: data["SessionId"]?.jsonPrimitive?.contentOrNull
			?: return null
		return ServerWebSocketMessage.SessionEnded(sessionId = sessionId)
	}

	private fun parseScheduledTaskEnded(data: JsonObject?): ServerWebSocketMessage.ScheduledTaskEnded? {
		data ?: return null
		val taskId = data["Id"]?.jsonPrimitive?.contentOrNull ?: return null
		val taskName = data["Name"]?.jsonPrimitive?.contentOrNull ?: return null
		val status = data["Status"]?.jsonPrimitive?.contentOrNull ?: return null
		return ServerWebSocketMessage.ScheduledTaskEnded(
			taskId = taskId,
			taskName = taskName,
			status = status,
		)
	}

	private fun JsonObject.stringList(key: String): List<String> =
		this[key]?.jsonArray
			?.mapNotNull { it.jsonPrimitive.contentOrNull }
			?: emptyList()
}
