package org.jellyfin.androidtv.ui.settings.screen.customization

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.constant.RatingType
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsCustomizationRatingTypeScreen() {
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	var enabledRatingsStr by rememberPreference(userSettingPreferences, UserSettingPreferences.enabledRatings)
	val enableAdditionalRatings by rememberPreference(userSettingPreferences, UserSettingPreferences.enableAdditionalRatings)

	var enabledRatings by remember(enabledRatingsStr) {
		mutableStateOf(
			enabledRatingsStr.split(",")
				.filter { it.isNotBlank() }
				.mapNotNull { name -> RatingType.entries.find { it.name == name } }
				.toSet()
		)
	}

	fun toggleRating(rating: RatingType) {
		enabledRatings = if (rating in enabledRatings) {
			enabledRatings - rating
		} else {
			enabledRatings + rating
		}
		enabledRatingsStr = enabledRatings.joinToString(",") { it.name }
	}

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_enabled_ratings)) },
				captionContent = { Text(stringResource(R.string.pref_enabled_ratings_description)) },
			)
		}

		items(RatingType.entries.filter { it != RatingType.RATING_HIDDEN }) { entry ->
			val requiresApiKey = entry !in listOf(
				RatingType.RATING_TOMATOES,
				RatingType.RATING_RT_AUDIENCE,
				RatingType.RATING_STARS
			)

			val isEnabled = !requiresApiKey || enableAdditionalRatings
			val isChecked = entry in enabledRatings
			
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				captionContent = if (!isEnabled) {
					{ Text(stringResource(R.string.pref_enable_additional_ratings)) }
				} else null,
				trailingContent = { Checkbox(checked = isChecked && isEnabled) },
				enabled = isEnabled,
				onClick = {
					if (isEnabled) {
						toggleRating(entry)
					}
				}
			)
		}
	}
}
