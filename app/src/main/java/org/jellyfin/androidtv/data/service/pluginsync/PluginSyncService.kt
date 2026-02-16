package org.jellyfin.androidtv.data.service.pluginsync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.JellyseerrRepository
import org.jellyfin.androidtv.data.service.UpdateCheckerService
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.preference.Preference
import org.jellyfin.preference.PreferenceEnum
import org.jellyfin.preference.store.SharedPreferenceStore
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Bidirectional settings sync service for the Moonfin server plugin.
 *
 * Direction: three-way merge with local-wins conflict resolution.
 *
 * A **snapshot** of all synced values is persisted after every successful sync.
 * On the next sync, each key is compared against the snapshot to determine
 * which side changed:
 *
 * | Local vs Snapshot | Server vs Snapshot | Result       |
 * |-------------------|--------------------|------------- |
 * | same              | same               | either (same)|
 * | changed           | same               | use local    |
 * | same              | changed            | use server   |
 * | both changed      | both changed       | local wins   |
 *
 * When no snapshot exists (first sync), server values take precedence so that
 * settings configured on the server dashboard are applied to new clients.
 *
 * **On Startup** (called from [syncOnStartup]):
 * 1. Pings `GET /Moonfin/Ping` to check plugin availability
 * 2. Fetches server settings via `GET /Moonfin/Settings`
 * 3. Loads the last-synced snapshot
 * 4. Three-way merges local, server, and snapshot
 * 5. Applies merged values locally and pushes to server
 * 6. Saves the merged result as the new snapshot
 * 7. Registers change listeners for push-on-change
 *
 * **On Every Settings Change** (via [SharedPreferences.OnSharedPreferenceChangeListener]):
 * - Saves to local storage immediately (handled by the preference store itself)
 * - If server is reachable, pushes all syncable settings to server (debounced 500ms)
 * - Updates the snapshot after each successful push
 *
 * **Cross-Client Behavior:**
 * - New device with no local settings → pulls from server, settings follow the user
 * - Settings changed on server are respected when the local value hasn't changed
 * - Sync runs once on session start, not continuously
 *
 * **Limitations:**
 * - No real-time push between clients (no WebSocket/polling)
 */
class PluginSyncService(
	private val context: Context,
	private val api: ApiClient,
	private val userPreferences: UserPreferences,
	private val userSettingPreferences: UserSettingPreferences,
	private val userRepository: UserRepository,
	private val jellyseerrRepository: JellyseerrRepository,
	private val updateCheckerService: UpdateCheckerService,
) {
	companion object {
		private const val TAG = "PluginSync"
		private const val PING_PATH = "/Moonfin/Ping"
		private const val SETTINGS_PATH = "/Moonfin/Settings"
		private const val JELLYSEERR_CONFIG_PATH = "/Moonfin/Jellyseerr/Config"
		private const val DEBOUNCE_MS = 500L
		private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
	}

	private val httpClient = OkHttpClient.Builder()
		.connectTimeout(10, TimeUnit.SECONDS)
		.readTimeout(10, TimeUnit.SECONDS)
		.writeTimeout(10, TimeUnit.SECONDS)
		.build()

	private val json = Json {
		ignoreUnknownKeys = true
		isLenient = true
		encodeDefaults = true
	}

	/** Whether the server plugin was reachable on the last ping. */
	@Volatile
	private var serverAvailable = false

	/** Debounce job for push-on-change. */
	private var pushJob: Job? = null

	/** Listeners currently registered for change tracking. */
	private val changeListeners = mutableListOf<Pair<SharedPreferenceStore, SharedPreferences.OnSharedPreferenceChangeListener>>()

	/** Coroutine scope for push-on-change debouncing. Uses IO dispatcher. */
	private var pushScope: CoroutineScope? = null

	/**
	 * Counter incremented after settings are applied from server sync.
	 * UI components (MainToolbar, LeftSidebarNavigation) observe this to
	 * re-read preferences when plugin sync updates them on startup.
	 */
	private val _syncCompletedCounter = MutableStateFlow(0)
	val syncCompletedCounter: StateFlow<Int> = _syncCompletedCounter.asStateFlow()

	/** SharedPreferences storing the last-synced snapshot for three-way merge. */
	private val snapshotPrefs: SharedPreferences by lazy {
		context.getSharedPreferences(PluginSyncConstants.SNAPSHOT_PREFS_NAME, Context.MODE_PRIVATE)
	}

	/**
	 * Run full startup sync. Call after session is established and API client is configured.
	 */
	suspend fun syncOnStartup() = withContext(Dispatchers.IO) {
		if (!userPreferences[UserPreferences.pluginSyncEnabled]) {
			Timber.d("$TAG: Plugin sync disabled, skipping")
			unregisterChangeListeners()
			return@withContext
		}

		val baseUrl = api.baseUrl
		val token = api.accessToken
		if (baseUrl.isNullOrBlank() || token.isNullOrBlank()) {
			Timber.w("$TAG: API not configured (no baseUrl or token)")
			return@withContext
		}

		serverAvailable = ping(baseUrl, token)
		if (!serverAvailable) {
			Timber.w("$TAG: Server plugin not reachable, skipping sync")
			return@withContext
		}

		val serverSettings = fetchServerSettings(baseUrl, token)
		val localSettings = collectLocalSettings()
		val snapshot = loadSnapshot()

		when {
			serverSettings != null -> {
				val merged = mergeThreeWay(localSettings, serverSettings, snapshot)
				applySettings(merged)
				pushSettings(baseUrl, token, merged)
				saveSnapshot(merged)
				_syncCompletedCounter.value++
				Timber.i("$TAG: Startup sync complete (three-way merge)")
			}
			else -> {
				pushSettings(baseUrl, token, localSettings)
				saveSnapshot(localSettings)
				Timber.i("$TAG: Startup sync complete (pushed local to server)")
			}
		}

		registerChangeListeners()
		checkForPluginUpdate(baseUrl, token)
	}

	/**
	 * Configure Jellyseerr proxy mode via Moonfin plugin.
	 * Must be called AFTER user is set (requires active Jellyfin user for cookie storage).
	 * Separated from [syncOnStartup] because settings sync must run before the user is published
	 * to prevent stale preference reads, while Jellyseerr needs the user ID.
	 */
	suspend fun configureJellyseerrProxy() = withContext(Dispatchers.IO) {
		if (!userPreferences[UserPreferences.pluginSyncEnabled]) return@withContext

		val baseUrl = api.baseUrl
		val token = api.accessToken
		if (baseUrl.isNullOrBlank() || token.isNullOrBlank()) return@withContext
		if (!serverAvailable) return@withContext

		fetchJellyseerrConfig(baseUrl, token)
		autoConfigureMoonfinProxy(baseUrl, token)
	}

	/**
	 * Silently checks for app updates via `/Moonfin/ClientUpdate` and caches the result
	 * in [UpdateCheckerService.latestPluginUpdateInfo].
	 */
	private suspend fun checkForPluginUpdate(baseUrl: String, token: String) {
		val result = updateCheckerService.checkForUpdateViaPlugin(baseUrl, token)
		result.onSuccess { updateInfo ->
			if (updateInfo != null && updateInfo.isNewer) {
				Timber.i("$TAG: Update available via plugin: ${updateInfo.version}")
			} else {
				Timber.d("$TAG: No update available via plugin")
			}
		}.onFailure { error ->
			Timber.d(error, "$TAG: Plugin update check failed")
		}
	}

	/**
	 * Push current local settings to the server immediately.
	 * Used when plugin sync is first enabled.
	 */
	suspend fun pushCurrentSettings() = withContext(Dispatchers.IO) {
		val baseUrl = api.baseUrl
		val token = api.accessToken
		if (baseUrl.isNullOrBlank() || token.isNullOrBlank()) return@withContext

		serverAvailable = ping(baseUrl, token)
		if (!serverAvailable) {
			Timber.w("$TAG: Cannot push — server plugin not reachable")
			return@withContext
		}

		val localSettings = collectLocalSettings()
		pushSettings(baseUrl, token, localSettings)
		saveSnapshot(localSettings)
		Timber.i("$TAG: Pushed current settings to server")

		registerChangeListeners()
	}

	/**
	 * Stop listening for changes. Called when sync is disabled or session ends.
	 */
	fun unregisterChangeListeners() {
		changeListeners.forEach { (store, listener) ->
			store.unregisterChangeListener(listener)
		}
		changeListeners.clear()
		pushJob?.cancel()
		pushJob = null
		pushScope = null
		Timber.d("$TAG: Change listeners unregistered")
	}

	/**
	 * Ping the Moonfin server plugin to check availability.
	 * `GET {baseUrl}/Moonfin/Ping`
	 */
	private fun ping(baseUrl: String, token: String): Boolean {
		return try {
			val request = Request.Builder()
				.url("$baseUrl$PING_PATH")
				.header("Authorization", "MediaBrowser Token=\"$token\"")
				.get()
				.build()
			val response = httpClient.newCall(request).execute()
			val success = response.isSuccessful
			response.close()
			Timber.d("$TAG: Ping ${if (success) "OK" else "FAILED"}")
			success
		} catch (e: Exception) {
			Timber.w(e, "$TAG: Ping failed")
			false
		}
	}

	/**
	 * Fetch settings from the server.
	 * `GET {baseUrl}/Moonfin/Settings`
	 *
	 * @return Flat key-value map of server settings, or null if unavailable.
	 */
	private fun fetchServerSettings(baseUrl: String, token: String): Map<String, Any?>? {
		return try {
			val request = Request.Builder()
				.url("$baseUrl$SETTINGS_PATH")
				.header("Authorization", "MediaBrowser Token=\"$token\"")
				.get()
				.build()
			val response = httpClient.newCall(request).execute()
			if (!response.isSuccessful) {
				Timber.w("$TAG: Fetch settings failed (${response.code})")
				response.close()
				return null
			}
			val body = response.body?.string()
			response.close()
			if (body.isNullOrBlank()) return null

			Timber.d("$TAG: Raw server response (${body.length} bytes)")

			val jsonObject = json.decodeFromString<JsonObject>(body)
			val rawMap = jsonObjectToMap(jsonObject)
			val mapped = rawMap.mapKeys { (key, _) -> toCamelCase(key) }

			Timber.d("$TAG: Server keys received: ${mapped.keys}")
			mapped
		} catch (e: Exception) {
			Timber.w(e, "$TAG: Fetch settings failed")
			null
		}
	}

	/**
	 * Fetch Jellyseerr configuration from the server and write the admin-configured URL locally.
	 * `GET {baseUrl}/Moonfin/Jellyseerr/Config`
	 *
	 * This is pull-only — the URL is admin-configured on the server and never pushed by clients.
	 */
	private fun fetchJellyseerrConfig(baseUrl: String, token: String) {
		try {
			val request = Request.Builder()
				.url("$baseUrl$JELLYSEERR_CONFIG_PATH")
				.header("Authorization", "MediaBrowser Token=\"$token\"")
				.get()
				.build()
			val response = httpClient.newCall(request).execute()
			if (!response.isSuccessful) {
				Timber.d("$TAG: Jellyseerr config not available (${response.code})")
				response.close()
				return
			}
			val body = response.body?.string()
			response.close()
			if (body.isNullOrBlank()) return

			val jsonObject = json.decodeFromString<JsonObject>(body)
			val camelCased = jsonObject.mapKeys { (key, _) -> toCamelCase(key) }

			val enabled = (camelCased["enabled"] as? JsonPrimitive)?.booleanOrNull ?: false
			val url = (camelCased["url"] as? JsonPrimitive)?.content
			val variant = (camelCased["variant"] as? JsonPrimitive)?.content ?: "jellyseerr"
			val displayName = (camelCased["displayName"] as? JsonPrimitive)?.content

			val jellyseerrPrefs = getJellyseerrPrefs() ?: return

			jellyseerrPrefs.putRawString(JellyseerrPreferences.moonfinVariant.key, variant)
			if (!displayName.isNullOrBlank()) {
				jellyseerrPrefs.putRawString(JellyseerrPreferences.moonfinDisplayName.key, displayName)
			}
			Timber.i("$TAG: Jellyseerr variant: $variant, displayName: $displayName")

			if (enabled && !url.isNullOrBlank()) {
				jellyseerrPrefs.putRawString(JellyseerrPreferences.serverUrl.key, url)
				Timber.i("$TAG: Jellyseerr URL set from server config: $url")
			} else {
				Timber.d("$TAG: Jellyseerr not enabled or no URL configured on server")
			}
		} catch (e: Exception) {
			Timber.w(e, "$TAG: Failed to fetch Jellyseerr config")
		}
	}

	/**
	 * Auto-configure Jellyseerr proxy mode when the Moonfin plugin is available.
	 * Checks if Jellyseerr is enabled on the server and sets up proxy routing.
	 */
	private suspend fun autoConfigureMoonfinProxy(baseUrl: String, token: String) {
		try {
			val result = jellyseerrRepository.configureWithMoonfin(baseUrl, token)
			result.onSuccess { status ->
				if (status.authenticated) {
					Timber.i("$TAG: Moonfin Jellyseerr proxy configured (authenticated)")
				} else if (status.enabled) {
					Timber.i("$TAG: Moonfin Jellyseerr proxy configured (not yet authenticated)")
				} else {
					Timber.d("$TAG: Jellyseerr not enabled on server plugin")
				}
			}.onFailure { error ->
				Timber.w(error, "$TAG: Failed to configure Moonfin Jellyseerr proxy")
			}
		} catch (e: Exception) {
			Timber.w(e, "$TAG: Error during Moonfin proxy auto-configure")
		}
	}

	/**
	 * Push settings to the server.
	 * `POST {baseUrl}/Moonfin/Settings`
	 */
	private fun pushSettings(baseUrl: String, token: String, settings: Map<String, Any?>) {
		try {
			val settingsObj = settingsToJsonObject(settings)
			val wrappedBody = JsonObject(mapOf(
				"settings" to settingsObj,
				"clientId" to JsonPrimitive(PluginSyncConstants.CLIENT_ID),
			))
			val jsonBody = json.encodeToString(JsonObject.serializer(), wrappedBody)
			val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
			val request = Request.Builder()
				.url("$baseUrl$SETTINGS_PATH")
				.header("Authorization", "MediaBrowser Token=\"$token\"")
				.post(requestBody)
				.build()
			val response = httpClient.newCall(request).execute()
			if (!response.isSuccessful) {
				Timber.w("$TAG: Push settings failed (${response.code})")
			}
			response.close()
		} catch (e: Exception) {
			Timber.w(e, "$TAG: Push settings failed")
		}
	}

	/**
	 * Read all syncable preferences from local stores into a flat key-value map.
	 */
	private fun collectLocalSettings(): Map<String, Any?> {
		val map = mutableMapOf<String, Any?>()

		for (sp in PluginSyncConstants.USER_PREFERENCES) {
			map[sp.serverKey] = readPreference(userPreferences, sp)
		}

		for (sp in PluginSyncConstants.USER_SETTING_PREFERENCES) {
			map[sp.serverKey] = readPreference(userSettingPreferences, sp)
		}

		val jellyseerrPrefs = getJellyseerrPrefs()
		if (jellyseerrPrefs != null) {
			for (sp in PluginSyncConstants.JELLYSEERR_PREFERENCES) {
				map[sp.serverKey] = readPreference(jellyseerrPrefs, sp)
			}
		}

		return map
	}

	/**
	 * Apply a settings map back into local preference stores.
	 * Only updates keys that are in the syncable set.
	 */
	private fun applySettings(settings: Map<String, Any?>) {
		val userPrefKeys = PluginSyncConstants.USER_PREFERENCES.associateBy { it.serverKey }
		val userSettingPrefKeys = PluginSyncConstants.USER_SETTING_PREFERENCES.associateBy { it.serverKey }
		val jellyseerrPrefKeys = PluginSyncConstants.JELLYSEERR_PREFERENCES.associateBy { it.serverKey }
		val jellyseerrPrefs = getJellyseerrPrefs()

		try {
			for ((key, value) in settings) {
				if (value == null) continue

				when {
					key in userPrefKeys -> writePreference(userPreferences, userPrefKeys[key]!!, value)
					key in userSettingPrefKeys -> writePreference(userSettingPreferences, userSettingPrefKeys[key]!!, value)
					key in jellyseerrPrefKeys && jellyseerrPrefs != null -> writePreference(jellyseerrPrefs, jellyseerrPrefKeys[key]!!, value)
				}
			}
		} catch (e: Exception) {
			Timber.e(e, "$TAG: Error applying settings")
		}
	}

	/**
	 * Three-way merge using the last-synced snapshot as a common ancestor.
	 *
	 * For each syncable key:
	 * - If local changed from snapshot but server didn't → use local
	 * - If server changed from snapshot but local didn't → use server
	 * - If both changed (conflict) → local wins
	 * - If neither changed → use local (same value)
	 * - If no snapshot exists (first sync) → server wins for all keys
	 */
	private fun mergeThreeWay(
		local: Map<String, Any?>,
		server: Map<String, Any?>,
		snapshot: Map<String, Any?>,
	): Map<String, Any?> {
		if (snapshot.isEmpty()) {
			Timber.d("$TAG: No snapshot — falling back to server-wins")
			return local + server
		}

		val allKeys = (local.keys + server.keys + snapshot.keys)
			.filter { it in PluginSyncConstants.ALL_SERVER_KEYS }
			.toSet()

		val merged = mutableMapOf<String, Any?>()
		for (key in allKeys) {
			val localVal = local[key]
			val serverVal = server[key]
			val snapshotVal = snapshot[key]

			val localChanged = normalizeForComparison(localVal) != normalizeForComparison(snapshotVal)
			val serverChanged = normalizeForComparison(serverVal) != normalizeForComparison(snapshotVal)

			val chosen = when {
				serverChanged && !localChanged -> {
					Timber.d("$TAG: Merge [$key] → server (server changed, local same)")
					serverVal
				}
				localChanged && serverChanged -> {
					Timber.d("$TAG: Merge [$key] → local (conflict, local wins)")
					localVal
				}
				else -> localVal
			}
			merged[key] = chosen
		}
		return merged
	}

	/**
	 * Normalize a value to a comparable string form so that type mismatches
	 * (e.g. Int 1 vs String "1", Boolean true vs String "true") don't cause
	 * false "changed" detections during three-way merge.
	 */
	private fun normalizeForComparison(value: Any?): String {
		return value?.toString() ?: ""
	}

	/**
	 * Register [SharedPreferences.OnSharedPreferenceChangeListener] on each preference store.
	 * On any syncable key change, debounce 500ms then push all settings to server.
	 */
	private fun registerChangeListeners() {
		unregisterChangeListeners()

		pushScope = CoroutineScope(Dispatchers.IO)

		val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
			if (key == null || key !in PluginSyncConstants.ALL_LOCAL_KEYS) return@OnSharedPreferenceChangeListener
			if (!userPreferences[UserPreferences.pluginSyncEnabled]) return@OnSharedPreferenceChangeListener
			if (!serverAvailable) return@OnSharedPreferenceChangeListener

			Timber.d("$TAG: Syncable preference changed: $key — scheduling push")
			scheduleDebouncedPush()
		}

		registerListenerOnStore(userPreferences, listener)
		registerListenerOnStore(userSettingPreferences, listener)

		val jellyseerrPrefs = getJellyseerrPrefs()
		if (jellyseerrPrefs != null) {
			registerListenerOnStore(jellyseerrPrefs, listener)
		}

		Timber.d("$TAG: Change listeners registered")
	}

	private fun registerListenerOnStore(
		store: SharedPreferenceStore,
		listener: SharedPreferences.OnSharedPreferenceChangeListener,
	) {
		store.registerChangeListener(listener)
		changeListeners.add(store to listener)
	}

	/**
	 * Schedule a debounced push to server. Cancels any pending push and waits [DEBOUNCE_MS]
	 * before executing. This prevents rapid-fire pushes when multiple settings change quickly.
	 */
	private fun scheduleDebouncedPush() {
		pushJob?.cancel()
		pushJob = pushScope?.launch {
			delay(DEBOUNCE_MS)

			val baseUrl = api.baseUrl ?: return@launch
			val token = api.accessToken ?: return@launch
			val settings = collectLocalSettings()
			pushSettings(baseUrl, token, settings)
			saveSnapshot(settings)
			Timber.d("$TAG: Debounced push complete")
		}
	}

	/**
	 * Read a single preference value from a store, returning a serializable type.
	 */
	@Suppress("UNCHECKED_CAST")
	private fun readPreference(store: SharedPreferenceStore, sp: SyncablePreference<*>): Any? {
		return when (sp.type) {
			SyncType.BOOLEAN -> store[sp.preference as Preference<Boolean>]
			SyncType.INT -> store[sp.preference as Preference<Int>]
			SyncType.LONG -> store[sp.preference as Preference<Long>]
			SyncType.FLOAT -> store[sp.preference as Preference<Float>]
			SyncType.STRING -> store[sp.preference as Preference<String>]
			SyncType.ENUM -> {
				val raw = store.getRawString(sp.preference.key, "")
				if (raw.isNotBlank()) {
					raw
				} else {
					val defaultEnum = sp.preference.defaultValue as? Enum<*>
					if (defaultEnum is PreferenceEnum) {
						defaultEnum.serializedName ?: defaultEnum.name
					} else {
						defaultEnum?.name ?: ""
					}
				}
			}
		}
	}

	/**
	 * Write a single preference value to a store from a deserialized value.
	 */
	@Suppress("UNCHECKED_CAST")
	private fun writePreference(store: SharedPreferenceStore, sp: SyncablePreference<*>, value: Any) {
		when (sp.type) {
			SyncType.BOOLEAN -> {
				val boolVal = when (value) {
					is Boolean -> value
					is String -> value.toBooleanStrictOrNull() ?: return
					else -> return
				}
				store[sp.preference as Preference<Boolean>] = boolVal
			}
			SyncType.INT -> {
				val intVal = when (value) {
					is Number -> value.toInt()
					is String -> value.toIntOrNull() ?: return
					else -> return
				}
				store[sp.preference as Preference<Int>] = intVal
			}
			SyncType.LONG -> {
				val longVal = when (value) {
					is Number -> value.toLong()
					is String -> value.toLongOrNull() ?: return
					else -> return
				}
				store[sp.preference as Preference<Long>] = longVal
			}
			SyncType.FLOAT -> {
				val floatVal = when (value) {
					is Number -> value.toFloat()
					is String -> value.toFloatOrNull() ?: return
					else -> return
				}
				store[sp.preference as Preference<Float>] = floatVal
			}
			SyncType.STRING -> {
				store.putRawString(sp.preference.key, value.toString())
			}
			SyncType.ENUM -> {
				// Server may send PascalCase (e.g. "Top") while local enum names are UPPER_CASE (e.g. "TOP").
				// Match case-insensitively against the enum constants and write the canonical name.
				val strVal = value.toString()
				val enumConstants = sp.preference.type.java.enumConstants
				val matched = enumConstants?.find { constant ->
					val enum = constant as? Enum<*> ?: return@find false
					val prefEnum = constant as? PreferenceEnum
					prefEnum?.serializedName?.equals(strVal, ignoreCase = true) == true ||
						enum.name.equals(strVal, ignoreCase = true)
				}
				val finalValue = when {
					matched is PreferenceEnum -> matched.serializedName ?: (matched as Enum<*>).name
					matched is Enum<*> -> matched.name
					else -> strVal
				}
				store.putRawString(sp.preference.key, finalValue)
			}
		}
	}

	/**
	 * Convert a [JsonObject] to a flat `Map<String, Any?>`.
	 */
	private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> {
		val map = mutableMapOf<String, Any?>()
		for ((key, element) in obj) {
			map[key] = jsonElementToValue(element)
		}
		return map
	}

	/**
	 * Convert a [JsonElement] to a Kotlin value.
	 */
	private fun jsonElementToValue(element: JsonElement): Any? {
		return when (element) {
			is JsonNull -> null
			is JsonPrimitive -> {
				when {
					element.isString -> element.content
					element.booleanOrNull != null -> element.booleanOrNull
					element.intOrNull != null -> element.intOrNull
					element.longOrNull != null -> element.longOrNull
					element.floatOrNull != null -> element.floatOrNull
					else -> element.content
				}
			}
			else -> element.toString()
		}
	}

	/**
	 * Convert a settings map to a [JsonObject] for the POST body.
	 */
	private fun settingsToJsonObject(settings: Map<String, Any?>): JsonObject {
		val elements = settings.mapValues { (_, value) ->
			when (value) {
				null -> JsonNull
				is Boolean -> JsonPrimitive(value)
				is Int -> JsonPrimitive(value)
				is Long -> JsonPrimitive(value)
				is Float -> JsonPrimitive(value)
				is Double -> JsonPrimitive(value)
				is String -> JsonPrimitive(value)
				else -> JsonPrimitive(value.toString())
			}
		}
		return JsonObject(elements)
	}

	/**
	 * Convert a PascalCase key to camelCase.
	 * The server returns PascalCase keys (C# convention); the sync pipeline uses camelCase.
	 */
	private fun toCamelCase(key: String): String {
		if (key.isEmpty()) return key
		return key[0].lowercaseChar() + key.substring(1)
	}

	/**
	 * Load the last-synced snapshot from disk.
	 * Returns an empty map if no snapshot exists (first sync).
	 */
	private fun loadSnapshot(): Map<String, Any?> {
		val all = snapshotPrefs.all
		if (all.isNullOrEmpty()) return emptyMap()

		val savedVersion = all[PluginSyncConstants.SNAPSHOT_VERSION_KEY] as? Int ?: 0
		if (savedVersion < PluginSyncConstants.SNAPSHOT_VERSION) {
			Timber.i("$TAG: Snapshot version outdated ($savedVersion → ${PluginSyncConstants.SNAPSHOT_VERSION}), clearing for fresh merge")
			snapshotPrefs.edit().clear().apply()
			return emptyMap()
		}

		val map = mutableMapOf<String, Any?>()
		for ((key, value) in all) {
			if (key in PluginSyncConstants.ALL_SERVER_KEYS) {
				map[key] = value
			}
		}
		return map
	}

	/**
	 * Persist the current merged settings as the snapshot baseline for the next sync.
	 */
	private fun saveSnapshot(settings: Map<String, Any?>) {
		val editor = snapshotPrefs.edit()
		editor.clear()
		editor.putInt(PluginSyncConstants.SNAPSHOT_VERSION_KEY, PluginSyncConstants.SNAPSHOT_VERSION)
		for ((key, value) in settings) {
			if (key !in PluginSyncConstants.ALL_SERVER_KEYS) continue
			when (value) {
				is Boolean -> editor.putBoolean(key, value)
				is Int -> editor.putInt(key, value)
				is Long -> editor.putLong(key, value)
				is Float -> editor.putFloat(key, value)
				is String -> editor.putString(key, value)
				null -> { /* skip nulls */ }
				else -> editor.putString(key, value.toString())
			}
		}
		editor.apply()
		Timber.d("$TAG: Snapshot saved (${settings.size} keys)")
	}

	/**
	 * Get the per-user JellyseerrPreferences for the current user.
	 */
	private fun getJellyseerrPrefs(): JellyseerrPreferences? {
		val userId = userRepository.currentUser.value?.id?.toString() ?: return null
		return JellyseerrPreferences.migrateToUserPreferences(context, userId)
	}
}
