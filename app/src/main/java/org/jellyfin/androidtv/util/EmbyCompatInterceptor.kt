package org.jellyfin.androidtv.util

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
	private val _embyTokens = ConcurrentHashMap<String, String>()
	private val _onTokenExpired = AtomicReference<(() -> Unit)?>(null)

	fun setServerType(type: ServerType) {
		_serverType.set(type)
	}

	fun setUserId(userId: String?) {
		_userId.set(userId)
	}

	fun registerEmbyServer(baseUrl: String, userId: String, accessToken: String? = null) {
		val normalized = baseUrl.trimEnd('/')
		_embyServers[normalized] = userId
		if (accessToken != null) _embyTokens[normalized] = accessToken
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

	private fun resolveAccessToken(request: okhttp3.Request): String? {
		val requestUrl = request.url.toString()
		for ((baseUrl, token) in _embyTokens) {
			if (requestUrl.startsWith(baseUrl)) return token
		}
		return null
	}

	private fun isStreamingPath(path: String): Boolean =
		STREAMING_PATH_PATTERN.containsMatchIn(path)

	override fun intercept(chain: Interceptor.Chain): Response {
		val original = chain.request()
		val embyUserId = resolveEmbyUserId(original)
		if (embyUserId == null) return chain.proceed(original)

		var request = rewriteRequest(original, embyUserId)
		if (request.url != original.url) {
			Timber.d("EmbyCompat: rewrote %s → %s", original.url.encodedPath, request.url)
		}

		val path = request.url.encodedPath
		val streaming = isStreamingPath(path)
		if (streaming) {
			val token = resolveAccessToken(request)
			if (token != null && request.url.queryParameter("api_key") == null) {
				val url = request.url.newBuilder().addQueryParameter("api_key", token).build()
				request = request.newBuilder().url(url).build()
			}
		}

		val response = chain.proceed(request)

		if (response.code == 401) {
			Timber.w("EmbyCompat: received 401 for %s", path)
			if (!streaming) {
				_onTokenExpired.get()?.invoke()
			}
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
		patched = patchMissingRequiredFields(patched, request.url.encodedPath)

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
			val newPath = rewritePath(path, userId)
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

		url = rewriteQueryParameters(url)

		val body = rewriteRequestBody(request)

		return when {
			url != request.url && body != null -> request.newBuilder().url(url).method(request.method, body).build()
			url != request.url -> request.newBuilder().url(url).build()
			body != null -> request.newBuilder().method(request.method, body).build()
			else -> request
		}
	}

	private fun rewritePath(path: String, userId: String): String? {
		USERS_ME_PATTERN.find(path)?.let { match ->
			val prefix = match.groupValues[1]
			val suffix = match.groupValues[2]
			return "$prefix/Users/$userId$suffix"
		}

		USERS_CONFIGURATION_PATTERN.find(path)?.let { match ->
			val prefix = match.groupValues[1]
			return "$prefix/Users/$userId/Configuration"
		}

		if (path.endsWith("/UserItems/Resume", ignoreCase = true)) {
			return path.substringBeforeLast("/UserItems/Resume", "") + "/Users/$userId/Items/Resume"
		}
		if (path.endsWith("/UserViews", ignoreCase = true)) {
			return path.substringBeforeLast("/UserViews", "") + "/Users/$userId/Views"
		}
		if (path.endsWith("/Items/Latest", ignoreCase = true)) {
			return path.substringBeforeLast("/Items/Latest", "") + "/Users/$userId/Items/Latest"
		}

		USER_FAVORITE_ITEMS_PATTERN.find(path)?.let { match ->
			val prefix = match.groupValues[1]
			val itemId = match.groupValues[2]
			return "$prefix/Users/$userId/FavoriteItems/$itemId"
		}

		USER_PLAYED_ITEMS_PATTERN.find(path)?.let { match ->
			val prefix = match.groupValues[1]
			val itemId = match.groupValues[2]
			return "$prefix/Users/$userId/PlayedItems/$itemId"
		}

		PLAYING_ITEMS_PROGRESS_PATTERN.find(path)?.let { match ->
			val prefix = match.groupValues[1]
			val itemId = match.groupValues[2]
			return "$prefix/Users/$userId/PlayingItems/$itemId/Progress"
		}

		PLAYING_ITEMS_PATTERN.find(path)?.let { match ->
			val prefix = match.groupValues[1]
			val itemId = match.groupValues[2]
			return "$prefix/Users/$userId/PlayingItems/$itemId"
		}

		USER_ITEMS_USERDATA_PATTERN.find(path)?.let { match ->
			val prefix = match.groupValues[1]
			val itemId = match.groupValues[2]
			return "$prefix/Users/$userId/Items/$itemId/UserData"
		}

		USER_ITEMS_RATING_PATTERN.find(path)?.let { match ->
			val prefix = match.groupValues[1]
			val itemId = match.groupValues[2]
			return "$prefix/Users/$userId/Items/$itemId/Rating"
		}

		// Single item fetch: /Items/{uuid} → /Users/{userId}/Items/{uuid}
		SINGLE_ITEM_PATTERN.find(path)?.let { match ->
			val prefix = match.groupValues[1]
			val itemId = match.groupValues[2]
			return "$prefix/Users/$userId/Items/$itemId"
		}

		return null
	}

	private val ITEM_TYPE_MAPPINGS = mapOf(
		"LiveTvProgram" to "Program",
		"LiveTvChannel" to "TvChannel",
	)

	private val ITEM_TYPE_PARAMS = setOf("includeItemTypes", "excludeItemTypes")

	private val JELLYFIN_ONLY_PARAMS = setOf(
		"streamOptions", "enableAudioVbrEncoding", "mediaSourceId",
		"allowVideoStreamCopy", "allowAudioStreamCopy",
	)

	private fun rewriteQueryParameters(url: okhttp3.HttpUrl): okhttp3.HttpUrl {
		val parameterNames = url.queryParameterNames
		if (parameterNames.isEmpty()) return url

		var needsRewrite = false
		for (name in parameterNames) {
			if (name in JELLYFIN_ONLY_PARAMS) { needsRewrite = true; break }
			for (value in url.queryParameterValues(name)) {
				if (value != null) {
					if (uuidToNumeric(value) != null) { needsRewrite = true; break }
					if (name in ITEM_TYPE_PARAMS && value in ITEM_TYPE_MAPPINGS) { needsRewrite = true; break }
				}
			}
			if (needsRewrite) break
		}
		if (!needsRewrite) return url

		val builder = url.newBuilder()
		for (n in parameterNames) builder.removeAllQueryParameters(n)
		for (n in parameterNames) {
			if (n in JELLYFIN_ONLY_PARAMS) continue
			for (v in url.queryParameterValues(n)) {
				var converted = if (v != null) uuidToNumeric(v) ?: v else v
				if (converted != null && n in ITEM_TYPE_PARAMS) {
					converted = ITEM_TYPE_MAPPINGS[converted] ?: converted
				}
				builder.addQueryParameter(n, converted)
			}
		}
		return builder.build()
	}

	private fun rewriteRequestBody(request: okhttp3.Request): okhttp3.RequestBody? {
		val body = request.body ?: return null
		val contentType = body.contentType()?.toString() ?: return null
		if (!contentType.contains("json", ignoreCase = true)) return null
		if (request.method != "POST" && request.method != "PUT") return null

		val buffer = okio.Buffer()
		body.writeTo(buffer)
		val json = buffer.readUtf8()
		if (json.isEmpty()) return null

		var patched = replaceUuidToNumericIds(json, UUID_ID_PATTERN)
		patched = replaceArrayUuidToNumericIds(patched)
		if (patched == json) return null

		return patched.toByteArray().toRequestBody("application/json".toMediaType())
	}

	private fun replaceUuidToNumericIds(json: String, pattern: Regex): String {
		return pattern.replace(json) { match ->
			val key = match.groupValues[1]
			val uuid = match.groupValues[2]
			val numeric = uuidToNumeric(uuid)
			if (numeric != null) "\"$key\":\"$numeric\"" else match.value
		}
	}

	private fun replaceArrayUuidToNumericIds(json: String): String {
		return UUID_ARRAY_ELEMENT_PATTERN.replace(json) { match ->
			val uuid = match.groupValues[1]
			val numeric = uuidToNumeric(uuid)
			if (numeric != null) "\"$numeric\"" else match.value
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

	private fun patchMissingRequiredFields(json: String, path: String): String {
		return try {
			val trimmed = json.trimStart()
			if (trimmed.startsWith("[")) {
				val arr = JSONArray(json)
				var modified = false
				for (i in 0 until arr.length()) {
					arr.optJSONObject(i)?.let { if (patchObjectTree(it)) modified = true }
				}
				if (modified) arr.toString() else json
			} else {
				val root = JSONObject(json)
				var modified = patchObjectTree(root)
				modified = patchEndpointFields(root, path) || modified
				if (modified) root.toString() else json
			}
		} catch (_: Exception) {
			json
		}
	}

	private fun patchObjectTree(obj: JSONObject): Boolean {
		var modified = false

		obj.optJSONObject("UserData")?.let { userData ->
			val parentId: String? = if (obj.has("Id")) obj.optString("Id") else null
			if (patchUserItemData(userData, parentId)) modified = true
		}

		obj.optJSONObject("Configuration")?.let { config ->
			if (patchUserConfiguration(config)) modified = true
		}

		obj.optJSONObject("Policy")?.let { policy ->
			if (patchUserPolicy(policy)) modified = true
		}

		if (obj.has("Policy") && !obj.has("HasConfiguredEasyPassword")) {
			obj.put("HasConfiguredEasyPassword", false); modified = true
		}

		modified = patchJsonArray(obj, "MediaSources", ::patchMediaSourceInfo) || modified
		modified = patchMediaStreams(obj) || modified
		modified = patchJsonArray(obj, "Chapters", ::patchChapterInfo) || modified

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

	private fun patchUserConfiguration(obj: JSONObject): Boolean {
		var modified = false

		if (!obj.has("DisplayCollectionsView")) {
			obj.put("DisplayCollectionsView", false); modified = true
		}
		if (!obj.has("PlayDefaultAudioTrack")) {
			obj.put("PlayDefaultAudioTrack", true); modified = true
		}
		if (!obj.has("DisplayMissingEpisodes")) {
			obj.put("DisplayMissingEpisodes", false); modified = true
		}
		if (!obj.has("EnableLocalPassword")) {
			obj.put("EnableLocalPassword", false); modified = true
		}
		if (!obj.has("SubtitleMode")) {
			obj.put("SubtitleMode", "Default"); modified = true
		}
		if (!obj.has("HidePlayedInLatest")) {
			obj.put("HidePlayedInLatest", true); modified = true
		}
		if (!obj.has("RememberAudioSelections")) {
			obj.put("RememberAudioSelections", true); modified = true
		}
		if (!obj.has("RememberSubtitleSelections")) {
			obj.put("RememberSubtitleSelections", true); modified = true
		}
		if (!obj.has("EnableNextEpisodeAutoPlay")) {
			obj.put("EnableNextEpisodeAutoPlay", true); modified = true
		}

		val uuidArrayFields = arrayOf("GroupedFolders", "OrderedViews", "LatestItemsExcludes", "MyMediaExcludes")
		for (field in uuidArrayFields) {
			if (!obj.has(field)) {
				obj.put(field, JSONArray()); modified = true
			} else {
				if (convertNumericIdsInArray(obj, field)) modified = true
			}
		}

		return modified
	}

	private fun convertNumericIdsInArray(obj: JSONObject, key: String): Boolean {
		val arr = obj.optJSONArray(key) ?: return false
		var modified = false
		for (i in 0 until arr.length()) {
			val value = arr.optString(i) ?: continue
			if (value.isNotEmpty() && !value.contains("-")) {
				arr.put(i, numericToUuid(value))
				modified = true
			}
		}
		return modified
	}

	private fun patchUserPolicy(obj: JSONObject): Boolean {
		var modified = false

		if (!obj.has("ForceRemoteSourceTranscoding")) {
			obj.put("ForceRemoteSourceTranscoding", false); modified = true
		}
		if (!obj.has("LoginAttemptsBeforeLockout")) {
			obj.put("LoginAttemptsBeforeLockout", -1); modified = true
		}
		if (!obj.has("MaxActiveSessions")) {
			obj.put("MaxActiveSessions", 0); modified = true
		}
		if (!obj.has("PasswordResetProviderId")) {
			obj.put("PasswordResetProviderId", ""); modified = true
		}
		if (!obj.has("SyncPlayAccess")) {
			obj.put("SyncPlayAccess", "CreateAndJoinGroups"); modified = true
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

		modified = patchMediaStreams(obj) || modified

		return modified
	}

	private fun patchMediaStreams(obj: JSONObject): Boolean {
		val streams = obj.optJSONArray("MediaStreams") ?: return false
		var modified = false
		var i = 0
		while (i < streams.length()) {
			val stream = streams.optJSONObject(i)
			if (stream != null && stream.optString("Type") == "Attachment") {
				streams.remove(i)
				modified = true
			} else {
				if (stream != null && patchMediaStream(stream)) modified = true
				i++
			}
		}
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

	private fun patchEndpointFields(obj: JSONObject, path: String): Boolean {
		var modified = false
		if (path.contains("/Branding/Configuration", ignoreCase = true)) {
			if (!obj.has("SplashscreenEnabled")) {
				obj.put("SplashscreenEnabled", false); modified = true
			}
		}
		if (path.contains("/DisplayPreferences", ignoreCase = true)) {
			val defaults = mapOf(
				"RememberIndexing" to false,
				"PrimaryImageHeight" to 250,
				"PrimaryImageWidth" to 250,
				"ScrollDirection" to "Horizontal",
				"ShowBackdrop" to true,
				"RememberSorting" to false,
				"ShowSidebar" to true
			)
			for ((key, value) in defaults) {
				if (!obj.has(key)) {
					obj.put(key, value); modified = true
				}
			}
		}
		return modified
	}

	companion object {
		private val STREAMING_PATH_PATTERN = Regex("/(Videos|Audio)/[^/]+/(stream|master\\.m3u8|main\\.m3u8)", RegexOption.IGNORE_CASE)

		// "SomeId":"12345" — quoted numeric string
		private val NUMERIC_ID_PATTERN = Regex("\"(\\w*Id)\"\\s*:\\s*\"(\\d+)\"")
		// "SomeId":927 — bare (unquoted) numeric value
		private val BARE_NUMERIC_ID_PATTERN = Regex("\"(\\w*Id)\"\\s*:\\s*(\\d+)(?=[,}\\]])")
		private val UUID_ID_PATTERN = Regex("\"(\\w*Id)\"\\s*:\\s*\"([0-9]{8}-[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{12})\"")
		// UUID inside a JSON array element: ["uuid", ...] or [..., "uuid"]
		private val UUID_ARRAY_ELEMENT_PATTERN = Regex("(?<=[\\[,])\\s*\"([0-9]{8}-[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{12})\"(?=\\s*[,\\]])")

		private val USER_FAVORITE_ITEMS_PATTERN = Regex("(.*)/UserFavoriteItems/(.+)", RegexOption.IGNORE_CASE)
		private val USER_PLAYED_ITEMS_PATTERN = Regex("(.*)/UserPlayedItems/(.+)", RegexOption.IGNORE_CASE)
		private val PLAYING_ITEMS_PROGRESS_PATTERN = Regex("(.*)/PlayingItems/([^/]+)/Progress$", RegexOption.IGNORE_CASE)
		private val PLAYING_ITEMS_PATTERN = Regex("(.*)/PlayingItems/([^/]+)$", RegexOption.IGNORE_CASE)
		private val USER_ITEMS_USERDATA_PATTERN = Regex("(.*)/UserItems/([^/]+)/UserData$", RegexOption.IGNORE_CASE)
		private val USER_ITEMS_RATING_PATTERN = Regex("(.*)/UserItems/([^/]+)/Rating$", RegexOption.IGNORE_CASE)
		private val SINGLE_ITEM_PATTERN = Regex("(.*)/Items/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$", RegexOption.IGNORE_CASE)
		private val USERS_ME_PATTERN = Regex("(.*)/Users/Me(/.*)?$", RegexOption.IGNORE_CASE)
		private val USERS_CONFIGURATION_PATTERN = Regex("(.*)/Users/Configuration$", RegexOption.IGNORE_CASE)

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
