package org.jellyfin.androidtv.ui.settings.screen.moonfin

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsMoonfinHomeRowsImageScreen() {
	val userSettingPreferences = koinInject<UserSettingPreferences>()

	var universalOverride by rememberPreference(userSettingPreferences, UserSettingPreferences.homeRowsUniversalOverride)
	var universalImageType by rememberPreference(userSettingPreferences, UserSettingPreferences.homeRowsUniversalImageType)

	// Get active home sections
	val activeHomeSections = remember { userSettingPreferences.activeHomesections }

	// Track per-row image types
	val perRowImageTypes = remember {
		mutableStateMapOf<HomeSectionType, ImageType>().apply {
			activeHomeSections.forEach { section ->
				put(section, userSettingPreferences.getHomeRowImageType(section))
			}
		}
	}

	val imageTypeOptions = listOf(
		ImageType.POSTER to stringResource(R.string.image_type_poster),
		ImageType.THUMB to stringResource(R.string.image_type_thumbnail),
		ImageType.BANNER to stringResource(R.string.image_type_banner)
	)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_appearance).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_home_rows_image_type)) },
			)
		}

		// Universal Override Section
		item { ListSection(headingContent = { Text(stringResource(R.string.pref_universal_override)) }) }

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_home_rows_universal_override)) },
				captionContent = { Text(stringResource(R.string.pref_home_rows_universal_override_description)) },
				trailingContent = { Checkbox(checked = universalOverride) },
				onClick = { universalOverride = !universalOverride }
			)
		}

		if (universalOverride) {
			item { ListSection(headingContent = { Text(stringResource(R.string.pref_home_rows_universal_image_type)) }) }

			items(imageTypeOptions) { (value, label) ->
				ListButton(
					headingContent = { Text(label) },
					trailingContent = { RadioButton(checked = universalImageType == value) },
					onClick = { universalImageType = value }
				)
			}
		} else {
			// Per-Row Settings
			item { ListSection(headingContent = { Text(stringResource(R.string.pref_home_rows_per_row)) }) }

			items(activeHomeSections) { sectionType ->
				val currentImageType = perRowImageTypes[sectionType] ?: ImageType.POSTER
				val imageTypeLabel = when (currentImageType) {
					ImageType.POSTER -> stringResource(R.string.image_type_poster)
					ImageType.THUMB -> stringResource(R.string.image_type_thumbnail)
					ImageType.BANNER -> stringResource(R.string.image_type_banner)
					ImageType.SQUARE -> stringResource(R.string.image_type_square)
				}

				ListButton(
					headingContent = { Text(stringResource(sectionType.nameRes)) },
					captionContent = { Text(imageTypeLabel) },
					onClick = {
						// Cycle through image types
						val newType = when (currentImageType) {
							ImageType.POSTER -> ImageType.THUMB
							ImageType.THUMB -> ImageType.BANNER
							ImageType.BANNER -> ImageType.SQUARE
							ImageType.SQUARE -> ImageType.POSTER
						}
						perRowImageTypes[sectionType] = newType
						userSettingPreferences.setHomeRowImageType(sectionType, newType)
					}
				)
			}
		}
	}
}
