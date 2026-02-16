package org.jellyfin.androidtv.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import org.jellyfin.androidtv.preference.UserPreferences
import org.koin.compose.koinInject

/**
 * Returns the user's selected focus border color from the app theme preference.
 * Use this wherever a focus border color is needed for cards, posters, icons, or nav items.
 */
@Composable
fun focusBorderColor(): Color {
	val userPreferences = koinInject<UserPreferences>()
	return remember { Color(userPreferences[UserPreferences.appTheme].colorValue) }
}
