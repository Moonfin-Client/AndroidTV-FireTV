package org.moonfin.server.emby.socket

import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.moonfin.server.core.model.EmbyConnectionState
import org.moonfin.server.core.model.ServerWebSocketMessage
import org.moonfin.server.emby.EmbyApiClient
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

class EmbyWebSocketClient(
	private val api: EmbyApiClient,
	private val audioManager: AudioManager,
) : ServerWebSocketApi {

	companion object {
		private const val MAX_RECONNECT_ATTEMPTS = 12
	}

	private val json = Json { ignoreUnknownKeys = true }
	private val _messages = MutableSharedFlow<ServerWebSocketMessage>(extraBufferCapacity = 64)
	override val messages: Flow<ServerWebSocketMessage> = _messages

	private val _connectionState = MutableStateFlow<EmbyConnectionState>(EmbyConnectionState.Disconnected)
	val connectionState: StateFlow<EmbyConnectionState> = _connectionState.asStateFlow()

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

		_connectionState.value = EmbyConnectionState.Connecting
		val wsUrl = buildWsUrl(api.baseUrl, token, api.deviceId)

		withContext(Dispatchers.IO) {
			val client = OkHttpClient.Builder()
				.pingInterval(30, TimeUnit.SECONDS)
				.connectTimeout(15, TimeUnit.SECONDS)
				.readTimeout(0, TimeUnit.SECONDS)
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
		_connectionState.value = EmbyConnectionState.Disconnected
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
			_connectionState.value = EmbyConnectionState.Connected
			scope.launch { postCapabilities() }
		}

		override fun onMessage(webSocket: WebSocket, text: String) {
			handleRawMessage(webSocket, text)
		}

		override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
			webSocket.close(code, reason)
		}

		override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
			Timber.w(t, "Emby WebSocket failure (code=%d)", response?.code ?: -1)
			keepAliveJob?.cancel()
			if (response?.code == 401) {
				_connectionState.value = EmbyConnectionState.TokenExpired
				return
			}
			_connectionState.value = EmbyConnectionState.Error(t)
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
			EmbyWebSocketMessageParser.parse(messageType, dataObj)?.let { msg ->
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
		if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
			Timber.w("Emby WebSocket max reconnect attempts (%d) reached", MAX_RECONNECT_ATTEMPTS)
			_connectionState.value = EmbyConnectionState.ServerUnreachable
			return
		}
		reconnectJob?.cancel()
		reconnectJob = scope.launch {
			val baseDelay = min(30_000L, 1_000L * (1L shl min(reconnectAttempt, 5)))
			val jitter = Random.nextLong(0, baseDelay / 2 + 1)
			val delayMs = baseDelay + jitter
			reconnectAttempt++
			Timber.i("Emby WebSocket reconnecting in %d ms (attempt %d)", delayMs, reconnectAttempt)
			delay(delayMs)
			connect()
		}
	}

	fun reconnectNow() {
		if (!api.isConfigured) return
		reconnectJob?.cancel()
		reconnectAttempt = 0
		_connectionState.value = EmbyConnectionState.Connecting
		scope.launch { connect() }
	}
}
