package org.jellyfin.androidtv.preference

import android.content.Context
import androidx.preference.PreferenceManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.preference.booleanPreference
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.intPreference
import org.jellyfin.preference.stringPreference
import org.jellyfin.preference.store.SharedPreferenceStore
import java.util.UUID

/**
 * User-specific settings and preferences.
 * 
 * Can be instantiated with a userId for per-user settings (like PIN codes),
 * or without userId for global user settings.
 */
class UserSettingPreferences(
	context: Context,
	userId: UUID? = null,
) : SharedPreferenceStore(
	sharedPreferences = if (userId != null) {
		// User-specific preferences (PIN codes, etc.)
		context.getSharedPreferences("user_settings_${userId}", Context.MODE_PRIVATE)
	} else {
		// Global user settings (shared across all users)
		PreferenceManager.getDefaultSharedPreferences(context)
	}
) {
	companion object {
		val skipBackLength = intPreference("skipBackLength", 10_000)
		val skipForwardLength = intPreference("skipForwardLength", 30_000)

		/**
		 * Duration in milliseconds to rewind when resuming from pause.
		 * Allows users to rewatch a few seconds they may have missed while paused.
		 */
		val unpauseRewindDuration = intPreference("unpauseRewindDuration", 0)

		/**
		 * Whether to show the item description/overview on the pause screen.
		 */
		val showDescriptionOnPause = booleanPreference("showDescriptionOnPause", false)
		
		// Media Bar settings
		val mediaBarEnabled = booleanPreference("mediaBarEnabled", true)
		val mediaBarContentType = stringPreference("mediaBarContentType", "both")
		val mediaBarItemCount = stringPreference("mediaBarItemCount", "10")
		val mediaBarOverlayOpacity = intPreference("mediaBarOverlayOpacity", 50)
		val mediaBarOverlayColor = stringPreference("mediaBarOverlayColor", "gray")
		val mediaBarSwapLayout = booleanPreference("mediaBarSwapLayout", false)
		
		// Home rows image type settings
		val homeRowsUniversalOverride = booleanPreference("homeRowsUniversalOverride", false)
		val homeRowsUniversalImageType = enumPreference("homeRowsUniversalImageType", org.jellyfin.androidtv.constant.ImageType.POSTER)
		
		// Background blur settings
		@Deprecated("Use detailsBackgroundBlurAmount or browsingBackgroundBlurAmount instead", ReplaceWith("detailsBackgroundBlurAmount"))
		val backgroundBlurAmount = intPreference("backgroundBlurAmount", 10)
		val detailsBackgroundBlurAmount = intPreference("detailsBackgroundBlurAmount", 10)
		val browsingBackgroundBlurAmount = intPreference("browsingBackgroundBlurAmount", 10)

		// Rating settings
		val enableAdditionalRatings = booleanPreference("enableAdditionalRatings", false)
		val mdblistApiKey = stringPreference("mdblistApiKey", "")
		val enableEpisodeRatings = booleanPreference("enableEpisodeRatings", false)
		val tmdbApiKey = stringPreference("tmdbApiKey", "")
		
		/**
		 * Comma-separated list of enabled rating types.
		 * Default: "RATING_TOMATOES,RATING_STARS" (RT and Community Rating)
		 */
		val enabledRatings = stringPreference("enabledRatings", "RATING_TOMATOES,RATING_STARS")

		// New home sections configuration (JSON storage)
		val homeSectionsJson = stringPreference("home_sections_config", "")
		
		// Legacy home section preferences (kept for migration)
		@Deprecated("Use homeSectionsJson instead")
		val homesection0 = enumPreference("homesection0", HomeSectionType.MEDIA_BAR)
		@Deprecated("Use homeSectionsJson instead")
		val homesection1 = enumPreference("homesection1", HomeSectionType.RESUME)
		@Deprecated("Use homeSectionsJson instead")
		val homesection2 = enumPreference("homesection2", HomeSectionType.RESUME_BOOK)
		@Deprecated("Use homeSectionsJson instead")
		val homesection3 = enumPreference("homesection3", HomeSectionType.NONE)
		@Deprecated("Use homeSectionsJson instead")
		val homesection4 = enumPreference("homesection4", HomeSectionType.NEXT_UP)
		@Deprecated("Use homeSectionsJson instead")
		val homesection5 = enumPreference("homesection5", HomeSectionType.LATEST_MEDIA)
		@Deprecated("Use homeSectionsJson instead")
		val homesection6 = enumPreference("homesection6", HomeSectionType.NONE)
		@Deprecated("Use homeSectionsJson instead")
		val homesection7 = enumPreference("homesection7", HomeSectionType.NONE)
		@Deprecated("Use homeSectionsJson instead")
		val homesection8 = enumPreference("homesection8", HomeSectionType.NONE)
		@Deprecated("Use homeSectionsJson instead")
		val homesection9 = enumPreference("homesection9", HomeSectionType.NONE)

		// Theme music settings
		val themeMusicEnabled = booleanPreference("themeMusicEnabled", false)
		val themeMusicVolume = intPreference("themeMusicVolume", 30) // 0-100
		val themeMusicOnHomeRows = booleanPreference("themeMusicOnHomeRows", false)

		/* Security */
		/**
		 * Optional PIN code for user account protection (stored as SHA-256 hash)
		 */
		val userPinHash = stringPreference("user_pin_hash", "")

		/**
		 * Whether PIN is enabled for this user
		 */
		val userPinEnabled = booleanPreference("user_pin_enabled", false)
	}

	private val json = Json { 
		ignoreUnknownKeys = true 
		encodeDefaults = true
	}

	@Deprecated("Use homeSectionsConfig instead")
	val homesections = listOf(
		homesection0,
		homesection1,
		homesection2,
		homesection3,
		homesection4,
		homesection5,
		homesection6,
		homesection7,
		homesection8,
		homesection9,
	)

	/**
	 * Get or set the home sections configuration.
	 */
	var homeSectionsConfig: List<HomeSectionConfig>
		get() {
			val jsonString = get(homeSectionsJson)
			if (jsonString.isBlank()) return HomeSectionConfig.defaults()
			
			return try {
				json.decodeFromString(jsonString)
			} catch (e: Exception) {
				HomeSectionConfig.defaults()
			}
		}
		set(value) {
			val jsonString = json.encodeToString(value)
			set(homeSectionsJson, jsonString)
		}

	/**
	 * Get the active home sections (enabled sections sorted by order).
	 */
	val activeHomesections: List<HomeSectionType>
		get() = homeSectionsConfig
			.filter { it.enabled }
			.sortedBy { it.order }
			.map { it.type }
	
	/**
	 * Get the image type for a specific home row, respecting universal override.
	 */
	fun getHomeRowImageType(sectionType: HomeSectionType): org.jellyfin.androidtv.constant.ImageType {
		// Check if universal override is enabled
		if (get(homeRowsUniversalOverride)) {
			return get(homeRowsUniversalImageType)
		}
		
		// Get per-row preference
		val key = "homeRowImageType_${sectionType.serializedName}"
		val value = sharedPreferences.getString(key, org.jellyfin.androidtv.constant.ImageType.POSTER.name)
		return try {
			org.jellyfin.androidtv.constant.ImageType.valueOf(value ?: org.jellyfin.androidtv.constant.ImageType.POSTER.name)
		} catch (e: IllegalArgumentException) {
			org.jellyfin.androidtv.constant.ImageType.POSTER
		}
	}

	/**
	 * Set the image type for a specific home row.
	 */
	fun setHomeRowImageType(sectionType: HomeSectionType, imageType: org.jellyfin.androidtv.constant.ImageType) {
		val key = "homeRowImageType_${sectionType.serializedName}"
		sharedPreferences.edit().putString(key, imageType.name).apply()
	}
	
	init {
		runMigrations {
			// v1.3.1 to v1.4.0
			migration(toVersion = 1) { prefs ->
				// Split backgroundBlurAmount into separate settings for details and browsing
				// Handle both String (old dropdown) and Int (already migrated) formats
				val oldBlurKey = "backgroundBlurAmount"
				var oldBlurAmount = 10 // default
				
				if (prefs.contains(oldBlurKey)) {
					try {
						// Try reading as String first (old dropdown format)
						val stringValue = prefs.getString(oldBlurKey, null)
						if (stringValue != null) {
							oldBlurAmount = stringValue.toIntOrNull() ?: 10
						}
					} catch (e: ClassCastException) {
						// Already stored as Int, read it directly
						oldBlurAmount = prefs.getInt(oldBlurKey, 10)
					}
				}
				
				putInt(detailsBackgroundBlurAmount.key, oldBlurAmount)
				putInt(browsingBackgroundBlurAmount.key, oldBlurAmount)
			}
			
			// Migrate from old slot-based system to new system
			migration(toVersion = 2) { prefs ->
				// Check if we already have the new config
				if (prefs.contains(homeSectionsJson.key)) {
					val existing = prefs.getString(homeSectionsJson.key, "")
					if (!existing.isNullOrBlank()) {
						return@migration // Already migrated
					}
				}
				
				// Read old home section preferences and build enabled sections list
				val enabledOldSections = listOf(
					homesection0, homesection1, homesection2, homesection3, homesection4,
					homesection5, homesection6, homesection7, homesection8, homesection9
				).mapIndexedNotNull { index, pref ->
					val typeString = prefs.getString(pref.key, HomeSectionType.NONE.serializedName)
					val type = HomeSectionType.entries.find { it.serializedName == typeString } 
						?: HomeSectionType.NONE
					if (type != HomeSectionType.NONE) {
						HomeSectionConfig(type = type, enabled = true, order = index)
					} else null
				}
				
				// Check if user had MEDIA_BAR enabled and set the new toggle accordingly
				val hadMediaBar = enabledOldSections.any { it.type == HomeSectionType.MEDIA_BAR }
				putBoolean(mediaBarEnabled.key, hadMediaBar)
				
				// Get default configs for all available section types (excluding MEDIA_BAR)
				val defaultConfigs = HomeSectionConfig.defaults()
				
				// Build final config: start with enabled old sections, but exclude MEDIA_BAR
				val enabledOldSectionsWithoutMediaBar = enabledOldSections.filter { it.type != HomeSectionType.MEDIA_BAR }
				val enabledTypes = enabledOldSectionsWithoutMediaBar.map { it.type }.toSet()
				val newConfigs = buildList {
					// Add all old enabled sections with their original order (excluding MEDIA_BAR)
					addAll(enabledOldSectionsWithoutMediaBar)
					
					// Add any section types from defaults that weren't in the old config (as disabled)
					val maxOrder = enabledOldSectionsWithoutMediaBar.maxOfOrNull { it.order } ?: -1
					defaultConfigs.forEach { defaultConfig ->
						if (defaultConfig.type !in enabledTypes) {
							add(defaultConfig.copy(
								enabled = false, 
								order = maxOrder + 1 + defaultConfig.order
							))
						}
					}
				}.sortedBy { it.order }
				
				// Save the new config
				val jsonString = json.encodeToString(newConfigs)
				putString(homeSectionsJson.key, jsonString)
			}
			
			// Migration 3: Add PLAYLISTS section if it doesn't exist (replaces old WATCHLIST)
			migration(toVersion = 3) { prefs ->
				val existingConfig = prefs.getString(homeSectionsJson.key, "")
				if (existingConfig.isNullOrBlank()) return@migration
				
				try {
					val configs = json.decodeFromString<List<HomeSectionConfig>>(existingConfig)
					// Check if PLAYLISTS already exists (either new or migrated from watchlist)
					val hasPlaylists = configs.any { it.type == HomeSectionType.PLAYLISTS }
					if (!hasPlaylists) {
						// Add PLAYLISTS as disabled at the end
						val maxOrder = configs.maxOfOrNull { it.order } ?: -1
						val updatedConfigs = configs + HomeSectionConfig(
							type = HomeSectionType.PLAYLISTS,
							enabled = false,
							order = maxOrder + 1
						)
						putString(homeSectionsJson.key, json.encodeToString(updatedConfigs))
					}
				} catch (e: Exception) {
					// If parsing fails, leave as-is (defaults will be used)
				}
			}
			
			// Migration 4: Migrate single rating preference to multi-select
			// Note: Old defaultRatingType is in UserPreferences (default shared prefs)
			// New enabledRatings is in UserSettingPreferences (same store for global)
			migration(toVersion = 4) { prefs ->
				// Check if enabledRatings already exists (skip if already migrated)
				val existingRatings = prefs.getString(enabledRatings.key, null)
				if (existingRatings != null) return@migration
				
				// Read old single rating type from the same shared preferences
				// (UserSettingPreferences uses default shared prefs when userId is null)
				val oldRatingType = prefs.getString("pref_rating_type", "RATING_TOMATOES")
				
				// Convert to new multi-select format
				// If user had a specific rating selected, enable both that and community rating
				val newEnabledRatings = when (oldRatingType) {
					"RATING_HIDDEN" -> "" // No ratings
					"RATING_STARS" -> "RATING_STARS" // Just community rating
					"RATING_TOMATOES" -> "RATING_TOMATOES,RATING_STARS" // RT + Stars (default)
					else -> "$oldRatingType,RATING_STARS" // User's preference + community rating
				}
				
				putString(enabledRatings.key, newEnabledRatings)
			}
		}
	}
}
