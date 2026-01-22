package org.jellyfin.androidtv.ui.settings.screen.moonfin

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.NavbarPosition
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsMoonfinNavbarPositionScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	var navbarPosition by rememberPreference(userPreferences, UserPreferences.navbarPosition)

	val options = listOf(
		NavbarPosition.TOP to stringResource(R.string.pref_navbar_position_top),
		NavbarPosition.LEFT to stringResource(R.string.pref_navbar_position_left)
	)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.moonfin_settings).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_navbar_position)) },
				captionContent = { Text(stringResource(R.string.pref_navbar_position_description)) }
			)
		}

		items(options) { (value, label) ->
			ListButton(
				headingContent = { Text(label) },
				trailingContent = { RadioButton(checked = navbarPosition == value) },
				onClick = {
					navbarPosition = value
					router.back()
				}
			)
		}
	}
}
