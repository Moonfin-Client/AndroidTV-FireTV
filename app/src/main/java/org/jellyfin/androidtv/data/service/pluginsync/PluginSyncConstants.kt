package org.jellyfin.androidtv.data.service.pluginsync

import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.preference.Preference

/**
 * Central registry of all preference keys that participate in plugin sync.
 *
 * Each [SyncablePreference] maps an Android [Preference] (with its local SharedPreferences key)
 * to a [serverKey] (the camelCase key used by the Moonfin server plugin and web client).
 *
 * Only settings managed by the Moonfin server plugin are listed here.
 * Sensitive keys (passwords, auth tokens) are excluded.
 * [UserPreferences.pluginSyncEnabled] itself is never synced (device-local control).
 */
object PluginSyncConstants {

	/** SharedPreferences file name for the last-synced snapshot (three-way merge baseline). */
	const val SNAPSHOT_PREFS_NAME = "moonfin_sync_snapshot"

	/** Client identifier sent with POST requests to the server. */
	const val CLIENT_ID = "moonfin-androidtv"

	/**
	 * Snapshot schema version. Increment when server key mappings change to
	 * force a snapshot reset on the next sync (server-wins fallback).
	 */
	const val SNAPSHOT_VERSION = 2

	/** Key stored inside [SNAPSHOT_PREFS_NAME] to track the snapshot schema version. */
	const val SNAPSHOT_VERSION_KEY = "_snapshot_version"

	/** Preference keys from [UserPreferences] that should be synced. */
	val USER_PREFERENCES: List<SyncablePreference<*>> = listOf(
		// Toolbar Customization
		SyncablePreference(UserPreferences.navbarPosition, SyncType.ENUM, "navbarPosition"),
		SyncablePreference(UserPreferences.showShuffleButton, SyncType.BOOLEAN, "showShuffleButton"),
		SyncablePreference(UserPreferences.showGenresButton, SyncType.BOOLEAN, "showGenresButton"),
		SyncablePreference(UserPreferences.showFavoritesButton, SyncType.BOOLEAN, "showFavoritesButton"),
		SyncablePreference(UserPreferences.showLibrariesInToolbar, SyncType.BOOLEAN, "showLibrariesInToolbar"),
		SyncablePreference(UserPreferences.shuffleContentType, SyncType.STRING, "shuffleContentType"),
		// Home Screen
		SyncablePreference(UserPreferences.mergeContinueWatchingNextUp, SyncType.BOOLEAN, "mergeContinueWatchingNextUp"),
		SyncablePreference(UserPreferences.enableMultiServerLibraries, SyncType.BOOLEAN, "enableMultiServerLibraries"),
		SyncablePreference(UserPreferences.enableFolderView, SyncType.BOOLEAN, "enableFolderView"),
		SyncablePreference(UserPreferences.confirmExit, SyncType.BOOLEAN, "confirmExit"),
		// Display & Appearance
		SyncablePreference(UserPreferences.seasonalSurprise, SyncType.STRING, "seasonalSurprise"),
	)

	/** Preference keys from [UserSettingPreferences] that should be synced. */
	val USER_SETTING_PREFERENCES: List<SyncablePreference<*>> = listOf(
		// Media Bar
		SyncablePreference(UserSettingPreferences.mediaBarEnabled, SyncType.BOOLEAN, "mediaBarEnabled"),
		SyncablePreference(UserSettingPreferences.mediaBarContentType, SyncType.STRING, "mediaBarContentType"),
		SyncablePreference(UserSettingPreferences.mediaBarItemCount, SyncType.STRING, "mediaBarItemCount"),
		SyncablePreference(UserSettingPreferences.mediaBarOverlayOpacity, SyncType.INT, "mediaBarOpacity"),
		SyncablePreference(UserSettingPreferences.mediaBarOverlayColor, SyncType.STRING, "mediaBarOverlayColor"),
		SyncablePreference(UserSettingPreferences.mediaBarSwapLayout, SyncType.BOOLEAN, "mediaBarSwapLayout"),
		// Theme Music
		SyncablePreference(UserSettingPreferences.themeMusicEnabled, SyncType.BOOLEAN, "themeMusicEnabled"),
		SyncablePreference(UserSettingPreferences.themeMusicVolume, SyncType.INT, "themeMusicVolume"),
		SyncablePreference(UserSettingPreferences.themeMusicOnHomeRows, SyncType.BOOLEAN, "themeMusicOnHomeRows"),
		// Display & Appearance
		SyncablePreference(UserSettingPreferences.homeRowsUniversalOverride, SyncType.BOOLEAN, "homeRowsImageTypeOverride"),
		SyncablePreference(UserSettingPreferences.homeRowsUniversalImageType, SyncType.ENUM, "homeRowsImageType"),
		SyncablePreference(UserSettingPreferences.detailsBackgroundBlurAmount, SyncType.INT, "detailsScreenBlur"),
		SyncablePreference(UserSettingPreferences.browsingBackgroundBlurAmount, SyncType.INT, "browsingBlur"),
		// Ratings
		SyncablePreference(UserSettingPreferences.enableAdditionalRatings, SyncType.BOOLEAN, "mdblistEnabled"),
		SyncablePreference(UserSettingPreferences.enableEpisodeRatings, SyncType.BOOLEAN, "tmdbEpisodeRatingsEnabled"),
		// Parental Controls
		SyncablePreference(UserSettingPreferences.userPinEnabled, SyncType.BOOLEAN, "userPinEnabled"),
		SyncablePreference(UserSettingPreferences.userPinHash, SyncType.STRING, "userPinHash"),
	)

	/** Preference keys from [JellyseerrPreferences] that should be synced. */
	val JELLYSEERR_PREFERENCES: List<SyncablePreference<*>> = listOf(
		SyncablePreference(JellyseerrPreferences.enabled, SyncType.BOOLEAN, "jellyseerrEnabled"),
		SyncablePreference(JellyseerrPreferences.apiKey, SyncType.STRING, "jellyseerrApiKey"),
		SyncablePreference(JellyseerrPreferences.blockNsfw, SyncType.BOOLEAN, "jellyseerrBlockNsfw"),
	)

	/**
	 * Local SharedPreferences keys for change listener detection.
	 * These are the raw key strings stored in SharedPreferences on the device.
	 */
	val ALL_LOCAL_KEYS: Set<String> by lazy {
		(USER_PREFERENCES + USER_SETTING_PREFERENCES + JELLYSEERR_PREFERENCES)
			.map { it.preference.key }
			.toSet()
	}

	/**
	 * Server-side keys used throughout the sync pipeline (maps, snapshots, HTTP).
	 */
	val ALL_SERVER_KEYS: Set<String> by lazy {
		(USER_PREFERENCES + USER_SETTING_PREFERENCES + JELLYSEERR_PREFERENCES)
			.map { it.serverKey }
			.toSet()
	}
}

/** Type metadata for a syncable preference. */
enum class SyncType {
	BOOLEAN, INT, LONG, FLOAT, STRING, ENUM
}

/**
 * Associates a [Preference] with its [SyncType] and [serverKey] for sync serialization.
 *
 * @property preference The Android preference (carries the local SharedPreferences key).
 * @property type The value type for serialization/deserialization.
 * @property serverKey The camelCase key used by the Moonfin server plugin and web client.
 */
data class SyncablePreference<T : Any>(
	val preference: Preference<T>,
	val type: SyncType,
	val serverKey: String,
)
