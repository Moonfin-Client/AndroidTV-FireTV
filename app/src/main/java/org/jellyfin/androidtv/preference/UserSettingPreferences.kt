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
		
		// Media Bar settings
		val mediaBarEnabled = booleanPreference("mediaBarEnabled", true)
		val mediaBarContentType = stringPreference("mediaBarContentType", "both")
		val mediaBarItemCount = stringPreference("mediaBarItemCount", "10")
		val mediaBarOverlayOpacity = intPreference("mediaBarOverlayOpacity", 50)
		val mediaBarOverlayColor = stringPreference("mediaBarOverlayColor", "gray")
		
		// Background blur settings
		@Deprecated("Use detailsBackgroundBlurAmount or browsingBackgroundBlurAmount instead", ReplaceWith("detailsBackgroundBlurAmount"))
		val backgroundBlurAmount = intPreference("backgroundBlurAmount", 10)
		val detailsBackgroundBlurAmount = intPreference("detailsBackgroundBlurAmount", 10)
		val browsingBackgroundBlurAmount = intPreference("browsingBackgroundBlurAmount", 10)

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
		}
	}
}
