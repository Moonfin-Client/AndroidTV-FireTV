package org.jellyfin.androidtv.ui.settings.screen.customization

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.constant.AppTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsCustomizationThemeScreen() {
	val router = LocalRouter.current
	val activity = LocalActivity.current
	val context = LocalContext.current
	val userRepository = koinInject<UserRepository>()
	val userId = userRepository.currentUser.collectAsState().value?.id
	val userSettingPreferences = remember(userId) { UserSettingPreferences(context, userId) }
	var focusColor by rememberPreference(userSettingPreferences, UserSettingPreferences.focusColor)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_focus_color)) },
			)
		}

		items(AppTheme.entries) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				trailingContent = { RadioButton(checked = focusColor == entry) },
				onClick = {
					if (focusColor != entry) {
						userSettingPreferences[UserSettingPreferences.focusColor] = entry
						focusColor = entry
						activity?.recreate()
					} else {
						router.back()
					}
				}
			)
		}
	}
}
