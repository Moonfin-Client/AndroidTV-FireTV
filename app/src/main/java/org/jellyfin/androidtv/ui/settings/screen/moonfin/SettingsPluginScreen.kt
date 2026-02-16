package org.jellyfin.androidtv.ui.settings.screen.moonfin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.pluginsync.PluginSyncService
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
import org.jellyfin.androidtv.ui.settings.screen.customization.getBlurLabel
import org.jellyfin.androidtv.ui.settings.screen.customization.getMediaBarItemCountLabel
import org.jellyfin.androidtv.ui.settings.screen.customization.getOverlayColorLabel
import org.jellyfin.androidtv.ui.settings.screen.customization.getSeasonalLabel
import org.jellyfin.androidtv.ui.settings.screen.customization.getShuffleContentTypeLabel
import org.koin.compose.koinInject

@Composable
fun SettingsPluginScreen() {
	val router = LocalRouter.current
	val coroutineScope = rememberCoroutineScope()
	val userPreferences = koinInject<UserPreferences>()
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	val pluginSyncService = koinInject<PluginSyncService>()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.settings).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_plugin_settings)) },
				captionContent = { Text(stringResource(R.string.pref_plugin_description)) },
			)
		}

		item {
			var pluginSyncEnabled by rememberPreference(userPreferences, UserPreferences.pluginSyncEnabled)
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_moonfin), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_plugin_sync_enable)) },
				captionContent = { Text(stringResource(R.string.pref_plugin_sync_description)) },
				trailingContent = { Checkbox(checked = pluginSyncEnabled) },
				onClick = {
					pluginSyncEnabled = !pluginSyncEnabled
					if (pluginSyncEnabled) {
						coroutineScope.launch {
							pluginSyncService.pushCurrentSettings()
							pluginSyncService.configureJellyseerrProxy()
						}
					} else {
						pluginSyncService.unregisterChangeListeners()
					}
				}
			)
		}

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
			var enableFolderView by rememberPreference(userPreferences, UserPreferences.enableFolderView)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_enable_folder_view)) },
				captionContent = { Text(stringResource(R.string.pref_enable_folder_view_description)) },
				trailingContent = { Checkbox(checked = enableFolderView) },
				onClick = { enableFolderView = !enableFolderView }
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

		item { ListSection(headingContent = { Text(stringResource(R.string.pref_enable_additional_ratings)) }) }

		item {
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
			var showRatingLabels by rememberPreference(userSettingPreferences, UserSettingPreferences.showRatingLabels)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_show_rating_labels)) },
				captionContent = { Text(stringResource(R.string.pref_show_rating_labels_description)) },
				trailingContent = { Checkbox(checked = showRatingLabels) },
				onClick = { showRatingLabels = !showRatingLabels }
			)
		}

		item { ListSection(headingContent = { Text(stringResource(R.string.pref_episode_ratings)) }) }

		item {
			var enableEpisodeRatings by rememberPreference(userSettingPreferences, UserSettingPreferences.enableEpisodeRatings)
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_star), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_episode_ratings)) },
				captionContent = { Text(stringResource(R.string.pref_episode_ratings_description)) },
				trailingContent = { Checkbox(checked = enableEpisodeRatings) },
				onClick = { enableEpisodeRatings = !enableEpisodeRatings }
			)
		}

		item { ListSection(headingContent = { Text(stringResource(R.string.jellyseerr)) }) }

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_jellyseerr_jellyfish), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.jellyseerr_settings)) },
				captionContent = { Text(stringResource(R.string.jellyseerr_settings_description)) },
				onClick = { router.push(Routes.JELLYSEERR) }
			)
		}

		item { ListSection(headingContent = { Text(stringResource(R.string.pref_parental_controls)) }) }

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_parental_controls)) },
				captionContent = { Text(stringResource(R.string.pref_parental_controls_description)) },
				onClick = { router.push(Routes.MOONFIN_PARENTAL_CONTROLS) }
			)
		}
	}
}


