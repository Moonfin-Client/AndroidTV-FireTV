package org.jellyfin.androidtv.preference

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.JellyseerrFetchLimit
import org.jellyfin.androidtv.constant.JellyseerrRowType
import org.jellyfin.preference.booleanPreference
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.store.SharedPreferenceStore
import org.jellyfin.preference.stringPreference
import timber.log.Timber

/**
 * Jellyseerr integration preferences - ALL settings are stored per-user.
 * Each Jellyfin user has their own Jellyseerr configuration including:
 * - Server connection (URL, auth, API keys)
 * - UI preferences (navigation, toolbar, NSFW filter)
 * - Request profiles and discover rows
 * 
 * The global preferences instance is only used during migration from older versions.
 */
class JellyseerrPreferences(context: Context, userId: String? = null) : SharedPreferenceStore(
	sharedPreferences = if (userId != null) {
		context.getSharedPreferences("jellyseerr_prefs_$userId", Context.MODE_PRIVATE)
	} else {
		// Legacy global preferences - only used for migration
		context.getSharedPreferences("jellyseerr_prefs", Context.MODE_PRIVATE)
	}
) {
	companion object {
		val enabled = booleanPreference("jellyseerr_enabled", false)
		val serverUrl = stringPreference("jellyseerr_server_url", "")
		val password = stringPreference("jellyseerr_password", "")
		val authMethod = stringPreference("jellyseerr_auth_method", "jellyfin")
		val localEmail = stringPreference("jellyseerr_local_email", "")
		val localPassword = stringPreference("jellyseerr_local_password", "")
		val apiKey = stringPreference("jellyseerr_api_key", "")
		val lastJellyfinUser = stringPreference("jellyseerr_last_jellyfin_user", "")
		val autoGenerateApiKey = booleanPreference("jellyseerr_auto_generate_api_key", true)
		val lastVerifiedTime = stringPreference("jellyseerr_last_verified", "")
		val lastConnectionSuccess = booleanPreference("jellyseerr_last_connection_success", false)

		// Moonfin plugin proxy mode
		val moonfinMode = booleanPreference("jellyseerr_moonfin_mode", false)
		val moonfinDisplayName = stringPreference("jellyseerr_moonfin_display_name", "")
		val moonfinJellyseerrUserId = stringPreference("jellyseerr_moonfin_user_id", "")

		val showInNavigation = booleanPreference("jellyseerr_show_in_navigation", true)
		val showInToolbar = booleanPreference("jellyseerr_show_in_toolbar", true)
		val showRequestStatus = booleanPreference("jellyseerr_show_request_status", true)
		val fetchLimit = enumPreference("jellyseerr_fetch_limit", JellyseerrFetchLimit.MEDIUM)
		val blockNsfw = booleanPreference("jellyseerr_block_nsfw", true)
		val rowsConfigJson = stringPreference("jellyseerr_rows_config", "")
		val hdMovieProfileId = stringPreference("jellyseerr_hd_movie_profile_id", "")
		val fourKMovieProfileId = stringPreference("jellyseerr_4k_movie_profile_id", "")
		val hdTvProfileId = stringPreference("jellyseerr_hd_tv_profile_id", "")
		val fourKTvProfileId = stringPreference("jellyseerr_4k_tv_profile_id", "")
		val hdMovieRootFolderId = stringPreference("jellyseerr_hd_movie_root_folder_id", "")
		val fourKMovieRootFolderId = stringPreference("jellyseerr_4k_movie_root_folder_id", "")
		val hdTvRootFolderId = stringPreference("jellyseerr_hd_tv_root_folder_id", "")
		val fourKTvRootFolderId = stringPreference("jellyseerr_4k_tv_root_folder_id", "")
		val hdMovieServerId = stringPreference("jellyseerr_hd_movie_server_id", "")
		val fourKMovieServerId = stringPreference("jellyseerr_4k_movie_server_id", "")
		val hdTvServerId = stringPreference("jellyseerr_hd_tv_server_id", "")
		val fourKTvServerId = stringPreference("jellyseerr_4k_tv_server_id", "")
		
		// Migration key to track if global->user migration has been done
		private const val MIGRATION_DONE_KEY = "jellyseerr_migration_v1_done"
		
		/**
		 * Migrate settings from global preferences to user-specific preferences.
		 * This handles the transition where server config was stored globally but now
		 * needs to be stored per-user.
		 * 
		 * @param context Application context
		 * @param userId The user ID to migrate settings to
		 * @return The user-specific preferences (with migrated data if applicable)
		 */
		fun migrateToUserPreferences(context: Context, userId: String): JellyseerrPreferences {
			val globalPrefs = context.getSharedPreferences("jellyseerr_prefs", Context.MODE_PRIVATE)
			val userPrefs = JellyseerrPreferences(context, userId)
			
			// Check if migration already done for this user
			if (userPrefs.sharedPreferences.getBoolean(MIGRATION_DONE_KEY, false)) {
				return userPrefs
			}
			
			// Check if global prefs have data worth migrating
			val globalServerUrl = globalPrefs.getString(serverUrl.key, "") ?: ""
			val globalEnabled = globalPrefs.getBoolean(enabled.key, false)
			
			if (globalServerUrl.isEmpty() && !globalEnabled) {
				// Nothing to migrate, mark as done
				userPrefs.sharedPreferences.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
				return userPrefs
			}
			
			// Check if user already has their own config (don't overwrite)
			val userServerUrl = userPrefs.get(serverUrl)
			if (userServerUrl.isNotEmpty()) {
				// User already has config, mark migration done and skip
				userPrefs.sharedPreferences.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
				return userPrefs
			}
			
			Timber.i("Jellyseerr: Migrating settings from global to user $userId")
			
			// Migrate all settings (server config, auth data, and UI preferences)
			userPrefs.apply {
				// Server config and auth
				set(enabled, globalPrefs.getBoolean(enabled.key, false))
				set(serverUrl, globalServerUrl)
				set(password, globalPrefs.getString(password.key, "") ?: "")
				set(authMethod, globalPrefs.getString(authMethod.key, "jellyfin") ?: "jellyfin")
				set(localEmail, globalPrefs.getString(localEmail.key, "") ?: "")
				set(localPassword, globalPrefs.getString(localPassword.key, "") ?: "")
				set(apiKey, globalPrefs.getString(apiKey.key, "") ?: "")
				set(lastConnectionSuccess, globalPrefs.getBoolean(lastConnectionSuccess.key, false))
				
				// UI preferences
				set(showInNavigation, globalPrefs.getBoolean(showInNavigation.key, true))
				set(showInToolbar, globalPrefs.getBoolean(showInToolbar.key, true))
				set(showRequestStatus, globalPrefs.getBoolean(showRequestStatus.key, true))
				set(blockNsfw, globalPrefs.getBoolean(blockNsfw.key, true))
				set(rowsConfigJson, globalPrefs.getString(rowsConfigJson.key, "") ?: "")
				
				// Fetch limit (enum stored as string)
				val fetchLimitStr = globalPrefs.getString(fetchLimit.key, null)
				if (fetchLimitStr != null) {
					sharedPreferences.edit().putString(fetchLimit.key, fetchLimitStr).apply()
				}
				
				// Profile/server IDs
				set(hdMovieProfileId, globalPrefs.getString(hdMovieProfileId.key, "") ?: "")
				set(fourKMovieProfileId, globalPrefs.getString(fourKMovieProfileId.key, "") ?: "")
				set(hdTvProfileId, globalPrefs.getString(hdTvProfileId.key, "") ?: "")
				set(fourKTvProfileId, globalPrefs.getString(fourKTvProfileId.key, "") ?: "")
				set(hdMovieRootFolderId, globalPrefs.getString(hdMovieRootFolderId.key, "") ?: "")
				set(fourKMovieRootFolderId, globalPrefs.getString(fourKMovieRootFolderId.key, "") ?: "")
				set(hdTvRootFolderId, globalPrefs.getString(hdTvRootFolderId.key, "") ?: "")
				set(fourKTvRootFolderId, globalPrefs.getString(fourKTvRootFolderId.key, "") ?: "")
				set(hdMovieServerId, globalPrefs.getString(hdMovieServerId.key, "") ?: "")
				set(fourKMovieServerId, globalPrefs.getString(fourKMovieServerId.key, "") ?: "")
				set(hdTvServerId, globalPrefs.getString(hdTvServerId.key, "") ?: "")
				set(fourKTvServerId, globalPrefs.getString(fourKTvServerId.key, "") ?: "")
			}
			
			// Mark migration as done
			userPrefs.sharedPreferences.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
			
			Timber.i("Jellyseerr: Migration complete for user $userId")
			
			return userPrefs
		}
	}

	private val json = Json { 
		ignoreUnknownKeys = true 
		encodeDefaults = true
	}

	var rowsConfig: List<JellyseerrRowConfig>
		get() {
			val jsonString = get(rowsConfigJson)
			if (jsonString.isBlank()) return JellyseerrRowConfig.defaults()
			
			return try {
				json.decodeFromString(jsonString)
			} catch (e: Exception) {
				JellyseerrRowConfig.defaults()
			}
		}
		set(value) {
			val jsonString = json.encodeToString(value)
			set(rowsConfigJson, jsonString)
		}

	val activeRows: List<JellyseerrRowType>
		get() = rowsConfig
			.filter { it.enabled }
			.sortedBy { it.order }
			.map { it.type }
}
