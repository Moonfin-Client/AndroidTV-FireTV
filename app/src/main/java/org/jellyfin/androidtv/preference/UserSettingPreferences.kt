package org.jellyfin.androidtv.preference

import android.content.Context
import androidx.preference.PreferenceManager
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
		val mediaBarItemCount = stringPreference("mediaBarItemCount", "10")
		val mediaBarOverlayOpacity = intPreference("mediaBarOverlayOpacity", 50)
		val mediaBarOverlayColor = stringPreference("mediaBarOverlayColor", "gray")
		
		// Background blur settings
		@Deprecated("Use detailsBackgroundBlurAmount or browsingBackgroundBlurAmount instead", ReplaceWith("detailsBackgroundBlurAmount"))
		val backgroundBlurAmount = intPreference("backgroundBlurAmount", 10)
		val detailsBackgroundBlurAmount = intPreference("detailsBackgroundBlurAmount", 10)
		val browsingBackgroundBlurAmount = intPreference("browsingBackgroundBlurAmount", 10)

		val homesection0 = enumPreference("homesection0", HomeSectionType.MEDIA_BAR)
		val homesection1 = enumPreference("homesection1", HomeSectionType.RESUME)
		val homesection2 = enumPreference("homesection2", HomeSectionType.RESUME_BOOK)
		val homesection3 = enumPreference("homesection3", HomeSectionType.NONE)
		val homesection4 = enumPreference("homesection4", HomeSectionType.NEXT_UP)
		val homesection5 = enumPreference("homesection5", HomeSectionType.LATEST_MEDIA)
		val homesection6 = enumPreference("homesection6", HomeSectionType.NONE)
		val homesection7 = enumPreference("homesection7", HomeSectionType.NONE)
		val homesection8 = enumPreference("homesection8", HomeSectionType.NONE)
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

	val activeHomesections
		get() = homesections
			.map(::get)
			.filterNot { it == HomeSectionType.NONE }
	
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
		}
	}
}
