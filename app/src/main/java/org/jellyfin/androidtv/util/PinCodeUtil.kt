package org.jellyfin.androidtv.util

import android.content.Context
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.startup.PinEntryDialog
import java.security.MessageDigest
import java.util.UUID

/**
 * Utility class for PIN code operations
 */
object PinCodeUtil {
	/**
	 * Check if PIN protection is enabled for a specific user
	 */
	fun isPinEnabled(context: Context, userId: UUID): Boolean {
		val prefs = UserSettingPreferences(context, userId)
		return prefs[UserSettingPreferences.userPinEnabled] &&
			prefs[UserSettingPreferences.userPinHash].isNotEmpty()
	}

	/**
	 * Verify PIN code for a user by showing a dialog
	 * @param onResult callback with true if PIN is correct, false otherwise
	 */
	fun verifyPin(context: Context, userId: UUID, onResult: (Boolean) -> Unit) {
		val prefs = UserSettingPreferences(context, userId)
		val storedHash = prefs[UserSettingPreferences.userPinHash]

		if (storedHash.isEmpty()) {
			// No PIN set, allow access
			onResult(true)
			return
		}

		PinEntryDialog.show(
			context = context,
			mode = PinEntryDialog.Mode.VERIFY,
			onComplete = { pin ->
				if (pin != null) {
					val enteredHash = hashPin(pin)
					onResult(enteredHash == storedHash)
				} else {
					// User cancelled
					onResult(false)
				}
			}
		)
	}

	/**
	 * Hash a PIN code using SHA-256
	 */
	fun hashPin(pin: String): String {
		val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
		return bytes.joinToString("") { "%02x".format(it) }
	}
}
