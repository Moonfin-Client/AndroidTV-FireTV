package org.jellyfin.androidtv.util

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.moonfin.server.core.model.ServerType
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * OkHttp interceptor that bridges Jellyfin SDK API calls to Emby servers.
 * Rewrites request URLs, converts numeric IDs to/from UUIDs, and injects
 * missing required fields in JSON responses.
 *
 * Supports both global (active session) and per-URL (multi-server) Emby detection.
 */
class EmbyCompatInterceptor : Interceptor {

	private val _serverType = AtomicReference(ServerType.JELLYFIN)
	private val _userId = AtomicReference<String?>(null)
	private val _embyServers = ConcurrentHashMap<String, String>()
	private val _onTokenExpired = AtomicReference<(() -> Unit)?>(null)

	fun setServerType(type: ServerType) {
		_serverType.set(type)
	}

	fun setUserId(userId: String?) {
		_userId.set(userId)
	}

	fun registerEmbyServer(baseUrl: String, userId: String) {
		_embyServers[baseUrl.trimEnd('/')] = userId
	}

	fun setOnTokenExpired(callback: (() -> Unit)?) {
		_onTokenExpired.set(callback)
	}

	private fun resolveEmbyUserId(request: okhttp3.Request): String? {
		val requestUrl = request.url.toString()
		for ((baseUrl, userId) in _embyServers) {
			if (requestUrl.startsWith(baseUrl)) return userId
		}
		if (_serverType.get() == ServerType.EMBY) return _userId.get()
		return null
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val original = chain.request()
		val embyUserId = resolveEmbyUserId(original)
		if (embyUserId == null) return chain.proceed(original)

		val request = rewriteRequest(original, embyUserId)
		if (request.url != original.url) {
			Timber.d("EmbyCompat: rewrote %s → %s", original.url.encodedPath, request.url)
		}

		val response = chain.proceed(request)

		if (response.code == 401) {
			Timber.w("EmbyCompat: received 401 for %s", request.url.encodedPath)
			_onTokenExpired.get()?.invoke()
			return response
		}

		if (!response.isSuccessful) return response

		val contentType = response.header("Content-Type") ?: return response
		if (!contentType.contains("json", ignoreCase = true)) return response

		val body = response.body ?: return response
		val originalBytes = body.bytes()
		val json = originalBytes.decodeToString()

		var patched = patchStartIndex(json)
		patched = replaceNumericIds(patched, NUMERIC_ID_PATTERN)
		patched = replaceNumericIds(patched, BARE_NUMERIC_ID_PATTERN)
		patched = patchMissingRequiredFields(patched)

		if (patched == json) {
			return response.newBuilder()
				.body(originalBytes.toResponseBody(body.contentType()))
				.build()
		}

		Timber.d("EmbyCompat: patched response for %s", request.url.encodedPath)
		return response.newBuilder()
			.body(patched.toByteArray().toResponseBody("application/json".toMediaType()))
			.build()
	}

	private fun rewriteRequest(request: okhttp3.Request, userId: String?): okhttp3.Request {
		var url = request.url
		val path = url.encodedPath

		if (userId != null) {
			val newPath = when {
				path.endsWith("/UserItems/Resume", ignoreCase = true) -> {
					val prefix = path.substringBeforeLast("/UserItems/Resume", "")
					"$prefix/Users/$userId/Items/Resume"
				}
				path.endsWith("/UserViews", ignoreCase = true) -> {
					val prefix = path.substringBeforeLast("/UserViews", "")
					"$prefix/Users/$userId/Views"
				}
				else -> null
			}
			if (newPath != null) {
				url = url.newBuilder().encodedPath(newPath).build()
			}
		}

		val segments = url.pathSegments.toMutableList()
		var pathChanged = false
		for (i in segments.indices) {
			val numeric = uuidToNumeric(segments[i])
			if (numeric != null) {
				segments[i] = numeric
				pathChanged = true
			}
		}
		if (pathChanged) {
			url = url.newBuilder()
				.encodedPath("/" + segments.joinToString("/"))
				.build()
		}

		return if (url != request.url) {
			request.newBuilder().url(url).build()
		} else {
			request
		}
	}

	private fun patchStartIndex(json: String): String {
		if (!json.contains("\"Items\"") || !json.contains("\"TotalRecordCount\"")) return json
		if (json.contains("\"StartIndex\"")) return json

		val idx = json.indexOf('{')
		if (idx < 0) return json

		return buildString(json.length + 20) {
			append(json, 0, idx + 1)
			append("\"StartIndex\":0,")
			append(json, idx + 1, json.length)
		}
	}

	private fun replaceNumericIds(json: String, pattern: Regex): String {
		return pattern.replace(json) { match ->
			val key = match.groupValues[1]
			val numericId = match.groupValues[2]
			"\"$key\":\"${numericToUuid(numericId)}\""
		}
	}

	private fun patchMissingRequiredFields(json: String): String {
		return try {
			val root = JSONObject(json)
			if (patchObjectTree(root)) root.toString() else json
		} catch (_: Exception) {
			json
		}
	}

	private fun patchObjectTree(obj: JSONObject): Boolean {
		var modified = false

		obj.optJSONObject("UserData")?.let { userData ->
			val parentId = obj.optString("Id", null)
			if (patchUserItemData(userData, parentId)) modified = true
		}

		modified = patchJsonArray(obj, "MediaSources", ::patchMediaSourceInfo) || modified
		modified = patchJsonArray(obj, "Chapters", ::patchChapterInfo) || modified

		// Strip LockedFields — Emby sends values (e.g. "SortName") absent from Jellyfin's MetadataField enum
		if (obj.has("LockedFields")) {
			obj.put("LockedFields", JSONArray())
			modified = true
		}

		modified = patchJsonArray(obj, "Items", ::patchObjectTree) || modified

		return modified
	}

	private fun patchJsonArray(parent: JSONObject, key: String, patcher: (JSONObject) -> Boolean): Boolean {
		val arr = parent.optJSONArray(key) ?: return false
		var modified = false
		for (i in 0 until arr.length()) {
			arr.optJSONObject(i)?.let { if (patcher(it)) modified = true }
		}
		return modified
	}

	private fun patchUserItemData(obj: JSONObject, parentId: String?): Boolean {
		var modified = false
		if (!obj.has("PlaybackPositionTicks")) {
			obj.put("PlaybackPositionTicks", 0L); modified = true
		}
		if (!obj.has("PlayCount")) {
			obj.put("PlayCount", 0); modified = true
		}
		if (!obj.has("IsFavorite")) {
			obj.put("IsFavorite", false); modified = true
		}
		if (!obj.has("Played")) {
			obj.put("Played", false); modified = true
		}
		if (!obj.has("Key")) {
			obj.put("Key", parentId ?: ""); modified = true
		}
		if (!obj.has("ItemId")) {
			obj.put("ItemId", parentId ?: "00000000-0000-0000-0000-000000000000")
			modified = true
		}
		return modified
	}

	private fun patchMediaSourceInfo(obj: JSONObject): Boolean {
		var modified = false

		if (!obj.has("Protocol")) {
			obj.put("Protocol", "File"); modified = true
		}
		if (!obj.has("Type")) {
			obj.put("Type", "Default"); modified = true
		}
		if (!obj.has("TranscodingSubProtocol")) {
			obj.put("TranscodingSubProtocol", "http"); modified = true
		}

		val booleanFieldsDefaultFalse = listOf(
			"IsRemote", "ReadAtNativeFramerate", "IgnoreDts", "IgnoreIndex",
			"GenPtsInput", "SupportsTranscoding", "SupportsDirectStream",
			"SupportsDirectPlay", "IsInfiniteStream", "RequiresOpening",
			"RequiresClosing", "RequiresLooping", "HasSegments"
		)
		for (field in booleanFieldsDefaultFalse) {
			if (!obj.has(field)) {
				obj.put(field, false); modified = true
			}
		}
		if (!obj.has("SupportsProbing")) {
			obj.put("SupportsProbing", true); modified = true
		}

		modified = patchJsonArray(obj, "MediaStreams", ::patchMediaStream) || modified

		return modified
	}

	private fun patchMediaStream(obj: JSONObject): Boolean {
		var modified = false
		if (!obj.has("Type")) {
			obj.put("Type", "Video"); modified = true
		}
		if (!obj.has("Index")) {
			obj.put("Index", 0); modified = true
		}
		val booleanFields = listOf(
			"IsInterlaced", "IsDefault", "IsForced",
			"IsHearingImpaired", "IsExternal", "IsTextSubtitleStream",
			"SupportsExternalStream"
		)
		for (field in booleanFields) {
			if (!obj.has(field)) {
				obj.put(field, false); modified = true
			}
		}
		return modified
	}

	private fun patchChapterInfo(obj: JSONObject): Boolean {
		var modified = false
		if (!obj.has("ImageDateModified")) {
			obj.put("ImageDateModified", "0001-01-01T00:00:00.0000000Z")
			modified = true
		}
		return modified
	}

	companion object {
		// "SomeId":"12345" — quoted numeric string
		private val NUMERIC_ID_PATTERN = Regex("\"(\\w*Id)\"\\s*:\\s*\"(\\d+)\"")
		// "SomeId":927 — bare (unquoted) numeric value
		private val BARE_NUMERIC_ID_PATTERN = Regex("\"(\\w*Id)\"\\s*:\\s*(\\d+)(?=[,}\\]])")

		fun numericToUuid(id: String): String {
			val padded = id.padStart(32, '0')
			return "${padded.substring(0, 8)}-${padded.substring(8, 12)}-" +
				"${padded.substring(12, 16)}-${padded.substring(16, 20)}-" +
				padded.substring(20, 32)
		}

		fun uuidToNumeric(value: String): String? {
			if (value.length != 36) return null
			val stripped = value.replace("-", "")
			if (stripped.length != 32) return null
			if (!stripped.all { it.isDigit() }) return null
			val num = stripped.trimStart('0')
			return num.ifEmpty { "0" }
		}
	}
}
