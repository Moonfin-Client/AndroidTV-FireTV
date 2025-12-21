package org.jellyfin.androidtv.ui.preference.screen

import android.content.Intent
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.AppTheme
import org.jellyfin.androidtv.preference.constant.ClockBehavior
import org.jellyfin.androidtv.preference.constant.RatingType
import org.jellyfin.androidtv.preference.constant.WatchedIndicatorBehavior
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.preference.PreferencesActivity
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.koin.compose.koinInject

@Composable
fun CustomizationPreferencesScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	val context = LocalContext.current

	SettingsColumn {
		// Theme section
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_theme)) },
			)
		}

		item {
			var appTheme by rememberPreference(userPreferences, UserPreferences.appTheme)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_app_theme)) },
				captionContent = { Text(stringResource(appTheme.nameRes)) },
				onClick = { /* Open theme selector */ }
			)
		}

		item {
			var clockBehavior by rememberPreference(userPreferences, UserPreferences.clockBehavior)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_clock_display)) },
				captionContent = { Text(stringResource(clockBehavior.nameRes)) },
				onClick = { /* Open clock behavior selector */ }
			)
		}

		item {
			var watchedIndicatorBehavior by rememberPreference(userPreferences, UserPreferences.watchedIndicatorBehavior)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_watched_indicator)) },
				captionContent = { Text(stringResource(watchedIndicatorBehavior.nameRes)) },
				onClick = { /* Open watched indicator selector */ }
			)
		}

		item {
			var backdropEnabled by rememberPreference(userPreferences, UserPreferences.backdropEnabled)
			ListButton(
				headingContent = { Text(stringResource(R.string.lbl_show_backdrop)) },
				captionContent = { Text(stringResource(R.string.pref_show_backdrop_description)) },
				trailingContent = { Checkbox(checked = backdropEnabled) },
				onClick = { backdropEnabled = !backdropEnabled }
			)
		}

		item {
			var seriesThumbnailsEnabled by rememberPreference(userPreferences, UserPreferences.seriesThumbnailsEnabled)
			ListButton(
				headingContent = { Text(stringResource(R.string.lbl_use_series_thumbnails)) },
				captionContent = { Text(stringResource(R.string.lbl_use_series_thumbnails_description)) },
				trailingContent = { Checkbox(checked = seriesThumbnailsEnabled) },
				onClick = { seriesThumbnailsEnabled = !seriesThumbnailsEnabled }
			)
		}

		item {
			var defaultRatingType by rememberPreference(userPreferences, UserPreferences.defaultRatingType)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_default_rating)) },
				captionContent = { Text(stringResource(defaultRatingType.nameRes)) },
				onClick = { /* Open rating type selector */ }
			)
		}

		item {
			var premieresEnabled by rememberPreference(userPreferences, UserPreferences.premieresEnabled)
			ListButton(
				headingContent = { Text(stringResource(R.string.lbl_show_premieres)) },
				captionContent = { Text(stringResource(R.string.desc_premieres)) },
				trailingContent = { Checkbox(checked = premieresEnabled) },
				onClick = { premieresEnabled = !premieresEnabled }
			)
		}

		item {
			var mediaManagementEnabled by rememberPreference(userPreferences, UserPreferences.mediaManagementEnabled)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_enable_media_management)) },
				captionContent = { Text(stringResource(R.string.pref_enable_media_management_description)) },
				trailingContent = { Checkbox(checked = mediaManagementEnabled) },
				onClick = { mediaManagementEnabled = !mediaManagementEnabled }
			)
		}

		// Browsing section
		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.pref_browsing)) },
				overlineContent = { Text(stringResource(R.string.app_name).uppercase()) },
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_house), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.home_prefs)) },
				captionContent = { Text(stringResource(R.string.pref_home_description)) },
				onClick = { router.push(Routes.HOME_SECTIONS) }
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_grid), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_libraries)) },
				captionContent = { Text(stringResource(R.string.pref_libraries_description)) },
				onClick = {
					val intent = Intent(context, PreferencesActivity::class.java).apply {
						putExtra(PreferencesActivity.EXTRA_SCREEN, LibrariesPreferencesScreen::class.qualifiedName)
						putExtra(PreferencesActivity.EXTRA_SCREEN_ARGS, android.os.Bundle())
					}
					context.startActivity(intent)
				}
			)
		}

		// Screensaver section
		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.pref_screensaver)) },
				overlineContent = { Text(stringResource(R.string.app_name).uppercase()) },
			)
		}

		item {
			var screensaverInAppEnabled by rememberPreference(userPreferences, UserPreferences.screensaverInAppEnabled)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_screensaver_inapp_enabled)) },
				captionContent = { Text(stringResource(R.string.pref_screensaver_inapp_enabled_description)) },
				trailingContent = { Checkbox(checked = screensaverInAppEnabled) },
				onClick = { screensaverInAppEnabled = !screensaverInAppEnabled }
			)
		}

		item {
			var screensaverAgeRatingRequired by rememberPreference(userPreferences, UserPreferences.screensaverAgeRatingRequired)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_screensaver_ageratingrequired_title)) },
				captionContent = { 
					Text(stringResource(
						if (screensaverAgeRatingRequired) R.string.pref_screensaver_ageratingrequired_enabled 
						else R.string.pref_screensaver_ageratingrequired_disabled
					))
				},
				trailingContent = { Checkbox(checked = screensaverAgeRatingRequired) },
				onClick = { screensaverAgeRatingRequired = !screensaverAgeRatingRequired }
			)
		}

		// Behavior section
		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.pref_behavior)) },
				overlineContent = { Text(stringResource(R.string.app_name).uppercase()) },
			)
		}

		item {
			var shortcutAudioTrack by rememberPreference(userPreferences, UserPreferences.shortcutAudioTrack)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_audio_track_button)) },
				captionContent = { Text(stringResource(R.string.pref_audio_track_button)) },
				onClick = { /* Open audio track shortcut selector */ }
			)
		}

		item {
			var shortcutSubtitleTrack by rememberPreference(userPreferences, UserPreferences.shortcutSubtitleTrack)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_subtitle_track_button)) },
				captionContent = { Text(stringResource(R.string.pref_subtitle_track_button)) },
				onClick = { /* Open subtitle track shortcut selector */ }
			)
		}
	}
}
