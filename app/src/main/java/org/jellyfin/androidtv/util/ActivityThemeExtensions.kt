package org.jellyfin.androidtv.util

import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.constant.AppTheme
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Private view model for the [applyTheme] extension to store the currently set theme.
 */
class ThemeViewModel : ViewModel() {
	var theme: AppTheme? = null
}

/**
 * Extension function to set the theme. Should be called in [FragmentActivity.onCreate] and
 * [FragmentActivity.onResume]. It recreates the activity when the theme changed after it was set.
 * Do not call during resume if the activity may not be recreated (like in the video player).
 *
 * The XML theme is always [R.style.Theme_Jellyfin] (dark). The focus color preference is per-user
 * and applied via Compose.
 */
fun FragmentActivity.applyTheme() {
	val viewModel by viewModels<ThemeViewModel>()
	val userRepository by inject<UserRepository>()
	val userId = userRepository.currentUser.value?.id
	val userSettingPreferences = UserSettingPreferences(this, userId)
	val theme = userSettingPreferences[UserSettingPreferences.focusColor]

	if (viewModel.theme != theme) {
		if (viewModel.theme != null) {
			Timber.i("Recreating activity to apply focus color change")
			viewModel.theme = null
			recreate()
		} else {
			Timber.i("Applying theme (focus color: $theme)")
			viewModel.theme = theme
			setTheme(R.style.Theme_Jellyfin)
		}
	}
}
