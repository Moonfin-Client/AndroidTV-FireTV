package org.jellyfin.androidtv.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.koin.compose.koinInject

/**
 * Returns the current user's selected focus border color preference.
 * Use this wherever a focus border color is needed for cards, posters, icons, or nav items.
 */
@Composable
fun focusBorderColor(): Color {
	val context = LocalContext.current
	val userRepository = koinInject<UserRepository>()
	val currentUser by userRepository.currentUser.collectAsState()
	val userId = currentUser?.id

	return remember(userId) {
		val prefs = UserSettingPreferences(context, userId)
		Color(prefs[UserSettingPreferences.focusColor].colorValue)
	}
}
