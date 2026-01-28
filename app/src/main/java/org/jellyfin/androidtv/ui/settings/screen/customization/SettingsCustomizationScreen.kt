package org.jellyfin.androidtv.ui.settings.screen.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsCustomizationScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.settings).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_customization)) },
			)
		}

		item {
			var appTheme by rememberPreference(userPreferences, UserPreferences.appTheme)

			ListButton(
				headingContent = { Text(stringResource(R.string.pref_app_theme)) },
				captionContent = { Text(stringResource(appTheme.nameRes)) },
				onClick = { router.push(Routes.CUSTOMIZATION_THEME) }
			)
		}

		item {
			var clockBehavior by rememberPreference(userPreferences, UserPreferences.clockBehavior)

			ListButton(
				headingContent = { Text(stringResource(R.string.pref_clock_display)) },
				captionContent = { Text(stringResource(clockBehavior.nameRes)) },
				onClick = { router.push(Routes.CUSTOMIZATION_CLOCK) }
			)
		}

		item {
			var watchedIndicatorBehavior by rememberPreference(userPreferences, UserPreferences.watchedIndicatorBehavior)

			ListButton(
				headingContent = { Text(stringResource(R.string.pref_watched_indicator)) },
				captionContent = { Text(stringResource(watchedIndicatorBehavior.nameRes)) },
				onClick = { router.push(Routes.CUSTOMIZATION_WATCHED_INDICATOR) }
			)
		}

		item {
			var backdropEnabled by rememberPreference(userPreferences, UserPreferences.backdropEnabled)

			ListButton(
				headingContent = { Text(stringResource(R.string.lbl_show_backdrop)) },
				trailingContent = { Checkbox(checked = backdropEnabled) },
				captionContent = { Text(stringResource(R.string.pref_show_backdrop_description)) },
				onClick = { backdropEnabled = !backdropEnabled }
			)
		}

		item {
			var seriesThumbnailsEnabled by rememberPreference(userPreferences, UserPreferences.seriesThumbnailsEnabled)

			ListButton(
				headingContent = { Text(stringResource(R.string.lbl_use_series_thumbnails)) },
				trailingContent = { Checkbox(checked = seriesThumbnailsEnabled) },
				captionContent = { Text(stringResource(R.string.lbl_use_series_thumbnails_description)) },
				onClick = { seriesThumbnailsEnabled = !seriesThumbnailsEnabled }
			)
		}

		item {
			val userSettingPreferences = koinInject<UserSettingPreferences>()
			val enabledRatingsStr by rememberPreference(userSettingPreferences, UserSettingPreferences.enabledRatings)
			val enabledCount = enabledRatingsStr.split(",").filter { it.isNotBlank() }.size

			ListButton(
				headingContent = { Text(stringResource(R.string.pref_enabled_ratings)) },
				captionContent = { Text(pluralStringResource(R.plurals.ratings_enabled, enabledCount, enabledCount)) },
				onClick = { router.push(Routes.CUSTOMIZATION_RATING_TYPE) }
			)
		}

		// Additional Ratings (Moonfin)
		item {
			val userSettingPreferences = koinInject<UserSettingPreferences>()
			var enableAdditionalRatings by rememberPreference(userSettingPreferences, UserSettingPreferences.enableAdditionalRatings)
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_star), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_enable_additional_ratings)) },
				captionContent = { Text(stringResource(R.string.pref_enable_additional_ratings_description)) },
				trailingContent = { Checkbox(checked = enableAdditionalRatings) },
				onClick = { enableAdditionalRatings = !enableAdditionalRatings }
			)
		}

		item {
			val userSettingPreferences = koinInject<UserSettingPreferences>()
			var mdblistApiKey by rememberPreference(userSettingPreferences, UserSettingPreferences.mdblistApiKey)
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_key), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_mdblist_api_key)) },
				captionContent = { Text(if (mdblistApiKey.isNotEmpty()) "API Key: ${mdblistApiKey.take(8)}..." else stringResource(R.string.pref_mdblist_api_key_description)) },
				onClick = { router.push(Routes.MOONFIN_MDBLIST_API_KEY) }
			)
		}

		// Toolbar Customization (Moonfin)
		item { ListSection(headingContent = { Text(stringResource(R.string.pref_toolbar_customization)) }) }

		item {
			val navbarPosition by rememberPreference(userPreferences, UserPreferences.navbarPosition)
			val navbarLabel = when (navbarPosition) {
				org.jellyfin.androidtv.preference.constant.NavbarPosition.TOP -> stringResource(R.string.pref_navbar_position_top)
				org.jellyfin.androidtv.preference.constant.NavbarPosition.LEFT -> stringResource(R.string.pref_navbar_position_left)
			}
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_navbar_position)) },
				captionContent = { Text(navbarLabel) },
				onClick = { router.push(Routes.MOONFIN_NAVBAR_POSITION) }
			)
		}

		item {
			var showShuffleButton by rememberPreference(userPreferences, UserPreferences.showShuffleButton)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_show_shuffle_button)) },
				captionContent = { Text(stringResource(R.string.pref_show_shuffle_button_description)) },
				trailingContent = { Checkbox(checked = showShuffleButton) },
				onClick = { showShuffleButton = !showShuffleButton }
			)
		}

		item {
			var showGenresButton by rememberPreference(userPreferences, UserPreferences.showGenresButton)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_show_genres_button)) },
				captionContent = { Text(stringResource(R.string.pref_show_genres_button_description)) },
				trailingContent = { Checkbox(checked = showGenresButton) },
				onClick = { showGenresButton = !showGenresButton }
			)
		}

		item {
			var showFavoritesButton by rememberPreference(userPreferences, UserPreferences.showFavoritesButton)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_show_favorites_button)) },
				captionContent = { Text(stringResource(R.string.pref_show_favorites_button_description)) },
				trailingContent = { Checkbox(checked = showFavoritesButton) },
				onClick = { showFavoritesButton = !showFavoritesButton }
			)
		}

		item {
			var showLibrariesInToolbar by rememberPreference(userPreferences, UserPreferences.showLibrariesInToolbar)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_show_libraries_in_toolbar)) },
				captionContent = { Text(stringResource(R.string.pref_show_libraries_in_toolbar_description)) },
				trailingContent = { Checkbox(checked = showLibrariesInToolbar) },
				onClick = { showLibrariesInToolbar = !showLibrariesInToolbar }
			)
		}

		item {
			val shuffleContentType by rememberPreference(userPreferences, UserPreferences.shuffleContentType)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_shuffle_content_type)) },
				captionContent = { Text(getShuffleContentTypeLabel(shuffleContentType)) },
				onClick = { router.push(Routes.MOONFIN_SHUFFLE_CONTENT_TYPE) }
			)
		}

		// Appearance (Moonfin)
		item { ListSection(headingContent = { Text(stringResource(R.string.pref_appearance)) }) }

		item {
			val seasonalSurprise by rememberPreference(userPreferences, UserPreferences.seasonalSurprise)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_seasonal_surprise)) },
				captionContent = { Text(getSeasonalLabel(seasonalSurprise)) },
				onClick = { router.push(Routes.MOONFIN_SEASONAL_SURPRISE) }
			)
		}

		item {
			val userSettingPreferences = koinInject<UserSettingPreferences>()
			val detailsBlur by rememberPreference(userSettingPreferences, UserSettingPreferences.detailsBackgroundBlurAmount)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_details_background_blur_amount)) },
				captionContent = { Text(getBlurLabel(detailsBlur)) },
				onClick = { router.push(Routes.MOONFIN_DETAILS_BLUR) }
			)
		}

		item {
			val userSettingPreferences = koinInject<UserSettingPreferences>()
			val browsingBlur by rememberPreference(userSettingPreferences, UserSettingPreferences.browsingBackgroundBlurAmount)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_browsing_background_blur_amount)) },
				captionContent = { Text(getBlurLabel(browsingBlur)) },
				onClick = { router.push(Routes.MOONFIN_BROWSING_BLUR) }
			)
		}

		// Theme Music (Moonfin)
		item { ListSection(headingContent = { Text(stringResource(R.string.pref_theme_music_title)) }) }

		item {
			val userSettingPreferences = koinInject<UserSettingPreferences>()
			var themeMusicEnabled by rememberPreference(userSettingPreferences, UserSettingPreferences.themeMusicEnabled)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_theme_music_enable)) },
				captionContent = { Text(stringResource(R.string.pref_theme_music_enable_summary)) },
				trailingContent = { Checkbox(checked = themeMusicEnabled) },
				onClick = { themeMusicEnabled = !themeMusicEnabled }
			)
		}

		item {
			val userSettingPreferences = koinInject<UserSettingPreferences>()
			val themeMusicEnabled by rememberPreference(userSettingPreferences, UserSettingPreferences.themeMusicEnabled)
			var themeMusicOnHomeRows by rememberPreference(userSettingPreferences, UserSettingPreferences.themeMusicOnHomeRows)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_theme_music_on_home_rows)) },
				captionContent = { Text(stringResource(R.string.pref_theme_music_on_home_rows_summary)) },
				trailingContent = { Checkbox(checked = themeMusicOnHomeRows) },
				enabled = themeMusicEnabled,
				onClick = { themeMusicOnHomeRows = !themeMusicOnHomeRows }
			)
		}

		item {
			val userSettingPreferences = koinInject<UserSettingPreferences>()
			val themeMusicEnabled by rememberPreference(userSettingPreferences, UserSettingPreferences.themeMusicEnabled)
			val themeMusicVolume by rememberPreference(userSettingPreferences, UserSettingPreferences.themeMusicVolume)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_theme_music_volume)) },
				captionContent = { Text("$themeMusicVolume%") },
				enabled = themeMusicEnabled,
				onClick = { router.push(Routes.MOONFIN_THEME_MUSIC_VOLUME) }
			)
		}

		// Parental Controls (Moonfin)
		item { ListSection(headingContent = { Text(stringResource(R.string.pref_parental_controls)) }) }

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_parental_controls)) },
				captionContent = { Text(stringResource(R.string.pref_parental_controls_description)) },
				onClick = { router.push(Routes.MOONFIN_PARENTAL_CONTROLS) }
			)
		}

		item {
			val userSettingPreferences = koinInject<UserSettingPreferences>()
			val themeMusicEnabled by rememberPreference(userSettingPreferences, UserSettingPreferences.themeMusicEnabled)
			var themeMusicOnHomeRows by rememberPreference(userSettingPreferences, UserSettingPreferences.themeMusicOnHomeRows)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_theme_music_on_home_rows)) },
				captionContent = { Text(stringResource(R.string.pref_theme_music_on_home_rows_summary)) },
				trailingContent = { Checkbox(checked = themeMusicOnHomeRows) },
				enabled = themeMusicEnabled,
				onClick = { themeMusicOnHomeRows = !themeMusicOnHomeRows }
			)
		}

		item {
			val userSettingPreferences = koinInject<UserSettingPreferences>()
			val themeMusicEnabled by rememberPreference(userSettingPreferences, UserSettingPreferences.themeMusicEnabled)
			val themeMusicVolume by rememberPreference(userSettingPreferences, UserSettingPreferences.themeMusicVolume)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_theme_music_volume)) },
				captionContent = { Text("$themeMusicVolume%") },
				enabled = themeMusicEnabled,
				onClick = { router.push(Routes.MOONFIN_THEME_MUSIC_VOLUME) }
			)
		}

		item { ListSection(headingContent = { Text(stringResource(R.string.pref_browsing)) }) }

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_grid), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_libraries)) },
				onClick = { router.push(Routes.LIBRARIES) }
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_house), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.home_prefs)) },
				onClick = { router.push(Routes.HOME) }
			)
		}
	}
}

@Composable
fun getShuffleContentTypeLabel(type: String): String = when (type) {
	"movies" -> stringResource(R.string.pref_shuffle_movies)
	"tv" -> stringResource(R.string.pref_shuffle_tv)
	"both" -> stringResource(R.string.pref_shuffle_both)
	else -> type
}

@Composable
private fun getSeasonalLabel(season: String): String = when (season) {
	"none" -> stringResource(R.string.pref_seasonal_none)
	"winter" -> stringResource(R.string.pref_seasonal_winter)
	"spring" -> stringResource(R.string.pref_seasonal_spring)
	"summer" -> stringResource(R.string.pref_seasonal_summer)
	"halloween" -> stringResource(R.string.pref_seasonal_halloween)
	"fall" -> stringResource(R.string.pref_seasonal_fall)
	else -> season
}

@Composable
private fun getBlurLabel(value: Int): String = when (value) {
	0 -> stringResource(R.string.pref_blur_none)
	5 -> stringResource(R.string.pref_blur_light)
	10 -> stringResource(R.string.pref_blur_medium)
	15 -> stringResource(R.string.pref_blur_strong)
	20 -> stringResource(R.string.pref_blur_extra_strong)
	else -> "${value}dp"
}
