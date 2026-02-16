package org.jellyfin.androidtv.ui.settings.screen.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
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
	val context = LocalContext.current
	val userPreferences = koinInject<UserPreferences>()
	val userRepository = koinInject<UserRepository>()
	val userId = userRepository.currentUser.collectAsState().value?.id
	val userSettingPreferences = remember(userId) { UserSettingPreferences(context, userId) }

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.settings).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_customization)) },
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

		item {
			var focusColor by rememberPreference(userSettingPreferences, UserSettingPreferences.focusColor)

			ListButton(
				headingContent = { Text(stringResource(R.string.pref_focus_color)) },
				captionContent = { Text(stringResource(focusColor.nameRes)) },
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
			var cardFocusExpansion by rememberPreference(userPreferences, UserPreferences.cardFocusExpansion)

			ListButton(
				headingContent = { Text(stringResource(R.string.lbl_card_focus_expansion)) },
				trailingContent = { Checkbox(checked = cardFocusExpansion) },
				captionContent = { Text(stringResource(R.string.lbl_card_focus_expansion_description)) },
				onClick = { cardFocusExpansion = !cardFocusExpansion }
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
fun getMediaBarItemCountLabel(count: String): String = when (count) {
	"5" -> stringResource(R.string.pref_media_bar_5_items)
	"10" -> stringResource(R.string.pref_media_bar_10_items)
	"15" -> stringResource(R.string.pref_media_bar_15_items)
	else -> count
}

@Composable
fun getOverlayColorLabel(color: String): String = when (color) {
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
fun getSeasonalLabel(season: String): String = when (season) {
	"none" -> stringResource(R.string.pref_seasonal_none)
	"winter" -> stringResource(R.string.pref_seasonal_winter)
	"spring" -> stringResource(R.string.pref_seasonal_spring)
	"summer" -> stringResource(R.string.pref_seasonal_summer)
	"halloween" -> stringResource(R.string.pref_seasonal_halloween)
	"fall" -> stringResource(R.string.pref_seasonal_fall)
	else -> season
}

@Composable
fun getBlurLabel(value: Int): String = when (value) {
	0 -> stringResource(R.string.pref_blur_none)
	5 -> stringResource(R.string.pref_blur_light)
	10 -> stringResource(R.string.pref_blur_medium)
	15 -> stringResource(R.string.pref_blur_strong)
	20 -> stringResource(R.string.pref_blur_extra_strong)
	else -> "${value}dp"
}
