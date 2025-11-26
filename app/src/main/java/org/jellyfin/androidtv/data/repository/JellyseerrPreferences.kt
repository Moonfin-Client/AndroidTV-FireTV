package org.jellyfin.androidtv.preference

import android.content.Context
import org.jellyfin.androidtv.constant.JellyseerrFetchLimit
import org.jellyfin.preference.booleanPreference
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.store.SharedPreferenceStore
import org.jellyfin.preference.stringPreference

/**
 * Jellyseerr integration preferences
 * Stores Jellyseerr server configuration and user settings
 */
class JellyseerrPreferences(context: Context) : SharedPreferenceStore(
	sharedPreferences = context.getSharedPreferences("jellyseerr_prefs", Context.MODE_PRIVATE)
) {
	companion object {
		/**
		 * Whether Jellyseerr integration is enabled
		 */
		val enabled = booleanPreference("jellyseerr_enabled", false)

		/**
		 * Jellyseerr server URL (e.g., https://jellyseerr.example.com)
		 */
		val serverUrl = stringPreference("jellyseerr_server_url", "")

	/**
	 * Jellyfin password for Jellyseerr authentication
	 */
	val password = stringPreference("jellyseerr_password", "")		/**
		 * Whether to show Jellyseerr in the main navigation
		 */
		val showInNavigation = booleanPreference("jellyseerr_show_in_navigation", true)

		/**
		 * Whether to show Jellyseerr button in the main toolbar
		 */
		val showInToolbar = booleanPreference("jellyseerr_show_in_toolbar", true)

		/**
		 * Whether to show request status in item details
		 */
		val showRequestStatus = booleanPreference("jellyseerr_show_request_status", true)

		/**
		 * Last time Jellyseerr connection was verified (timestamp)
		 */
		val lastVerifiedTime = stringPreference("jellyseerr_last_verified", "")

		/**
		 * Whether the last connection test was successful
		 */
		val lastConnectionSuccess = booleanPreference("jellyseerr_last_connection_success", false)

		/**
		 * Number of items to fetch per request (25, 50, or 75)
		 */
		val fetchLimit = enumPreference("jellyseerr_fetch_limit", JellyseerrFetchLimit.MEDIUM)
	}
}
