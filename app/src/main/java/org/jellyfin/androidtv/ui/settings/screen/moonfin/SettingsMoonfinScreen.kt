package org.jellyfin.androidtv.ui.settings.screen.moonfin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
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
fun SettingsMoonfinScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	val userSettingPreferences = koinInject<UserSettingPreferences>()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.settings).uppercase()) },
				headingContent = { Text(stringResource(R.string.moonfin_settings)) },
			)
		}

		// Toolbar Section
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

		// Home Screen Section
		item { ListSection(headingContent = { Text(stringResource(R.string.home_section_settings)) }) }

		item {
			var mergeContinueWatchingNextUp by rememberPreference(userPreferences, UserPreferences.mergeContinueWatchingNextUp)
			ListButton(
				headingContent = { Text(stringResource(R.string.lbl_merge_continue_watching_next_up)) },
				captionContent = { Text(stringResource(R.string.lbl_merge_continue_watching_next_up_description)) },
				trailingContent = { Checkbox(checked = mergeContinueWatchingNextUp) },
				onClick = { mergeContinueWatchingNextUp = !mergeContinueWatchingNextUp }
			)
		}

		item {
			var enableMultiServerLibraries by rememberPreference(userPreferences, UserPreferences.enableMultiServerLibraries)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_multi_server_libraries)) },
				captionContent = { Text(stringResource(R.string.pref_multi_server_libraries_description)) },
				trailingContent = { Checkbox(checked = enableMultiServerLibraries) },
				onClick = { enableMultiServerLibraries = !enableMultiServerLibraries }
			)
		}

		item {
			var confirmExit by rememberPreference(userPreferences, UserPreferences.confirmExit)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_confirm_exit)) },
				captionContent = { Text(stringResource(R.string.pref_confirm_exit_description)) },
				trailingContent = { Checkbox(checked = confirmExit) },
				onClick = { confirmExit = !confirmExit }
			)
		}

		// Media Bar Section
		item { ListSection(headingContent = { Text(stringResource(R.string.pref_media_bar_title)) }) }

		item {
			var mediaBarEnabled by rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarEnabled)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_media_bar_enable)) },
				captionContent = { Text(stringResource(R.string.pref_media_bar_enable_summary)) },
				trailingContent = { Checkbox(checked = mediaBarEnabled) },
				onClick = { mediaBarEnabled = !mediaBarEnabled }
			)
		}

		item {
			val mediaBarEnabled by rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarEnabled)
			val mediaBarContentType by rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarContentType)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_media_bar_content_type)) },
				captionContent = { Text(getShuffleContentTypeLabel(mediaBarContentType)) },
				enabled = mediaBarEnabled,
				onClick = { router.push(Routes.MOONFIN_MEDIA_BAR_CONTENT_TYPE) }
			)
		}

		item {
			val mediaBarEnabled by rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarEnabled)
			val mediaBarItemCount by rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarItemCount)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_media_bar_item_count)) },
				captionContent = { Text(getMediaBarItemCountLabel(mediaBarItemCount)) },
				enabled = mediaBarEnabled,
				onClick = { router.push(Routes.MOONFIN_MEDIA_BAR_ITEM_COUNT) }
			)
		}

		item {
			val mediaBarEnabled by rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarEnabled)
			val mediaBarOverlayOpacity by rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarOverlayOpacity)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_media_bar_overlay_opacity)) },
				captionContent = { Text("$mediaBarOverlayOpacity%") },
				enabled = mediaBarEnabled,
				onClick = { router.push(Routes.MOONFIN_MEDIA_BAR_OPACITY) }
			)
		}

		item {
			val mediaBarEnabled by rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarEnabled)
			val mediaBarOverlayColor by rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarOverlayColor)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_media_bar_overlay_color)) },
				captionContent = { Text(getOverlayColorLabel(mediaBarOverlayColor)) },
				enabled = mediaBarEnabled,
				onClick = { router.push(Routes.MOONFIN_MEDIA_BAR_COLOR) }
			)
		}

		// Theme Music Section
		item { ListSection(headingContent = { Text(stringResource(R.string.pref_theme_music_title)) }) }

		item {
			var themeMusicEnabled by rememberPreference(userSettingPreferences, UserSettingPreferences.themeMusicEnabled)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_theme_music_enable)) },
				captionContent = { Text(stringResource(R.string.pref_theme_music_enable_summary)) },
				trailingContent = { Checkbox(checked = themeMusicEnabled) },
				onClick = { themeMusicEnabled = !themeMusicEnabled }
			)
		}

		item {
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
			val themeMusicEnabled by rememberPreference(userSettingPreferences, UserSettingPreferences.themeMusicEnabled)
			val themeMusicVolume by rememberPreference(userSettingPreferences, UserSettingPreferences.themeMusicVolume)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_theme_music_volume)) },
				captionContent = { Text("$themeMusicVolume%") },
				enabled = themeMusicEnabled,
				onClick = { router.push(Routes.MOONFIN_THEME_MUSIC_VOLUME) }
			)
		}

		// Appearance Section
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
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_grid), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_home_rows_image_type)) },
				onClick = { router.push(Routes.MOONFIN_HOME_ROWS_IMAGE) }
			)
		}

		item {
			val detailsBlur by rememberPreference(userSettingPreferences, UserSettingPreferences.detailsBackgroundBlurAmount)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_details_background_blur_amount)) },
				captionContent = { Text(getBlurLabel(detailsBlur)) },
				onClick = { router.push(Routes.MOONFIN_DETAILS_BLUR) }
			)
		}

		item {
			val browsingBlur by rememberPreference(userSettingPreferences, UserSettingPreferences.browsingBackgroundBlurAmount)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_browsing_background_blur_amount)) },
				captionContent = { Text(getBlurLabel(browsingBlur)) },
				onClick = { router.push(Routes.MOONFIN_BROWSING_BLUR) }
			)
		}

		// Playback Section
		item { ListSection(headingContent = { Text(stringResource(R.string.pref_playback)) }) }

		item {
			var subtitlesDefaultToNone by rememberPreference(userPreferences, UserPreferences.subtitlesDefaultToNone)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_subtitles_default_to_none)) },
				captionContent = { Text(stringResource(R.string.pref_subtitles_default_to_none_description)) },
				trailingContent = { Checkbox(checked = subtitlesDefaultToNone) },
				onClick = { subtitlesDefaultToNone = !subtitlesDefaultToNone }
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_parental_controls)) },
				captionContent = { Text(stringResource(R.string.pref_parental_controls_description)) },
				onClick = { router.push(Routes.MOONFIN_PARENTAL_CONTROLS) }
			)
		}

		// SyncPlay Section
		item { ListSection(headingContent = { Text(stringResource(R.string.syncplay)) }) }

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_syncplay), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_syncplay_settings)) },
				captionContent = { Text(stringResource(R.string.pref_syncplay_description)) },
				onClick = { router.push(Routes.MOONFIN_SYNCPLAY) }
			)
		}
	}
}

@Composable
private fun getShuffleContentTypeLabel(type: String): String = when (type) {
	"movies" -> stringResource(R.string.pref_shuffle_movies)
	"tv" -> stringResource(R.string.pref_shuffle_tv)
	"both" -> stringResource(R.string.pref_shuffle_both)
	else -> type
}

@Composable
private fun getMediaBarItemCountLabel(count: String): String = when (count) {
	"5" -> stringResource(R.string.pref_media_bar_5_items)
	"10" -> stringResource(R.string.pref_media_bar_10_items)
	"15" -> stringResource(R.string.pref_media_bar_15_items)
	else -> count
}

@Composable
private fun getOverlayColorLabel(color: String): String = when (color) {
	"black" -> stringResource(R.string.pref_media_bar_color_black)
	"gray" -> stringResource(R.string.pref_media_bar_color_gray)
	"dark_blue" -> stringResource(R.string.pref_media_bar_color_dark_blue)
	"purple" -> stringResource(R.string.pref_media_bar_color_purple)
	"teal" -> stringResource(R.string.pref_media_bar_color_teal)
	"navy" -> stringResource(R.string.pref_media_bar_color_navy)
	"charcoal" -> stringResource(R.string.pref_media_bar_color_charcoal)
	"brown" -> stringResource(R.string.pref_media_bar_color_brown)
	"dark_red" -> stringResource(R.string.pref_media_bar_color_dark_red)
	"dark_green" -> stringResource(R.string.pref_media_bar_color_dark_green)
	"slate" -> stringResource(R.string.pref_media_bar_color_slate)
	"indigo" -> stringResource(R.string.pref_media_bar_color_indigo)
	else -> color
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
