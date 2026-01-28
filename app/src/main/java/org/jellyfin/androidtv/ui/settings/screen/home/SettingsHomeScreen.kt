package org.jellyfin.androidtv.ui.settings.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.preference.HomeSectionConfig
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsHomeScreen() {
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	val userPreferences = koinInject<org.jellyfin.androidtv.preference.UserPreferences>()
	val router = org.jellyfin.androidtv.ui.navigation.LocalRouter.current
	
	var sections by remember { mutableStateOf(userSettingPreferences.homeSectionsConfig) }
	var focusedSectionType by remember { mutableStateOf<HomeSectionType?>(null) }
	
	// Auto-focus the first item on initial load
	LaunchedEffect(Unit) {
		val firstSection = sections.sortedBy { it.order }.firstOrNull { it.type != HomeSectionType.NONE }
		if (firstSection != null) {
			focusedSectionType = firstSection.type
		}
	}
	
	// Auto-save when sections change
	val saveSections = { newSections: List<HomeSectionConfig> ->
		sections = newSections
		userSettingPreferences.homeSectionsConfig = newSections
	}

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.home_prefs)) },
				captionContent = { Text(stringResource(R.string.home_sections_description)) },
			)
		}
		
		// Home Screen Settings (Moonfin features)
		item {
			var mergeContinueWatchingNextUp by org.jellyfin.androidtv.ui.settings.compat.rememberPreference(userPreferences, org.jellyfin.androidtv.preference.UserPreferences.mergeContinueWatchingNextUp)
			ListButton(
				headingContent = { Text(stringResource(R.string.lbl_merge_continue_watching_next_up)) },
				captionContent = { Text(stringResource(R.string.lbl_merge_continue_watching_next_up_description)) },
				trailingContent = { Checkbox(checked = mergeContinueWatchingNextUp) },
				onClick = { mergeContinueWatchingNextUp = !mergeContinueWatchingNextUp }
			)
		}

		item {
			var enableMultiServerLibraries by org.jellyfin.androidtv.ui.settings.compat.rememberPreference(userPreferences, org.jellyfin.androidtv.preference.UserPreferences.enableMultiServerLibraries)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_multi_server_libraries)) },
				captionContent = { Text(stringResource(R.string.pref_multi_server_libraries_description)) },
				trailingContent = { Checkbox(checked = enableMultiServerLibraries) },
				onClick = { enableMultiServerLibraries = !enableMultiServerLibraries }
			)
		}

		item {
			var enableFolderView by org.jellyfin.androidtv.ui.settings.compat.rememberPreference(userPreferences, org.jellyfin.androidtv.preference.UserPreferences.enableFolderView)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_enable_folder_view)) },
				captionContent = { Text(stringResource(R.string.pref_enable_folder_view_description)) },
				trailingContent = { Checkbox(checked = enableFolderView) },
				onClick = { enableFolderView = !enableFolderView }
			)
		}

		item {
			var confirmExit by org.jellyfin.androidtv.ui.settings.compat.rememberPreference(userPreferences, org.jellyfin.androidtv.preference.UserPreferences.confirmExit)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_confirm_exit)) },
				captionContent = { Text(stringResource(R.string.pref_confirm_exit_description)) },
				trailingContent = { Checkbox(checked = confirmExit) },
				onClick = { confirmExit = !confirmExit }
			)
		}

		// Home Rows Image Type (Moonfin)
		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_grid), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_home_rows_image_type)) },
				onClick = { router.push(org.jellyfin.androidtv.ui.settings.Routes.MOONFIN_HOME_ROWS_IMAGE) }
			)
		}

		// Media Bar Settings (Moonfin)
		item { ListSection(headingContent = { Text(stringResource(R.string.pref_media_bar_title)) }) }

		item {
			var mediaBarEnabled by org.jellyfin.androidtv.ui.settings.compat.rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarEnabled)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_media_bar_enable)) },
				captionContent = { Text(stringResource(R.string.pref_media_bar_enable_summary)) },
				trailingContent = { Checkbox(checked = mediaBarEnabled) },
				onClick = { mediaBarEnabled = !mediaBarEnabled }
			)
		}

		item {
			val mediaBarEnabled by org.jellyfin.androidtv.ui.settings.compat.rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarEnabled)
			val mediaBarContentType by org.jellyfin.androidtv.ui.settings.compat.rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarContentType)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_media_bar_content_type)) },
				captionContent = { Text(org.jellyfin.androidtv.ui.settings.screen.customization.getShuffleContentTypeLabel(mediaBarContentType)) },
				enabled = mediaBarEnabled,
				onClick = { router.push(org.jellyfin.androidtv.ui.settings.Routes.MOONFIN_MEDIA_BAR_CONTENT_TYPE) }
			)
		}

		item {
			val mediaBarEnabled by org.jellyfin.androidtv.ui.settings.compat.rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarEnabled)
			val mediaBarItemCount by org.jellyfin.androidtv.ui.settings.compat.rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarItemCount)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_media_bar_item_count)) },
				captionContent = { Text(getMediaBarItemCountLabel(mediaBarItemCount)) },
				enabled = mediaBarEnabled,
				onClick = { router.push(org.jellyfin.androidtv.ui.settings.Routes.MOONFIN_MEDIA_BAR_ITEM_COUNT) }
			)
		}

		item {
			val mediaBarEnabled by org.jellyfin.androidtv.ui.settings.compat.rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarEnabled)
			val mediaBarOverlayOpacity by org.jellyfin.androidtv.ui.settings.compat.rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarOverlayOpacity)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_media_bar_overlay_opacity)) },
				captionContent = { Text("$mediaBarOverlayOpacity%") },
				enabled = mediaBarEnabled,
				onClick = { router.push(org.jellyfin.androidtv.ui.settings.Routes.MOONFIN_MEDIA_BAR_OPACITY) }
			)
		}

		item {
			val mediaBarEnabled by org.jellyfin.androidtv.ui.settings.compat.rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarEnabled)
			val mediaBarOverlayColor by org.jellyfin.androidtv.ui.settings.compat.rememberPreference(userSettingPreferences, UserSettingPreferences.mediaBarOverlayColor)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_media_bar_overlay_color)) },
				captionContent = { Text(getOverlayColorLabel(mediaBarOverlayColor)) },
				enabled = mediaBarEnabled,
				onClick = { router.push(org.jellyfin.androidtv.ui.settings.Routes.MOONFIN_MEDIA_BAR_COLOR) }
			)
		}

		item { ListSection(headingContent = { Text(stringResource(R.string.home_sections_description)) }) }
		
		val configurableSections = sections
			.filter { it.type != HomeSectionType.MEDIA_BAR }
			.sortedBy { it.order }
		
		configurableSections.forEachIndexed { index, section ->
			if (section.type != HomeSectionType.NONE) {
				item(key = section.type) {
					HomeSectionRow(
						section = section,
						canMoveUp = index > 0,
						canMoveDown = index < configurableSections.size - 1,
						shouldRequestFocus = section.type == focusedSectionType,
						onFocusChanged = { focused ->
							if (focused) focusedSectionType = section.type
						},
						onToggle = {
							val updated = sections.map {
								if (it.type == section.type) it.copy(enabled = !it.enabled)
								else it
							}
							saveSections(updated)
						},
						onMoveUp = {
							if (index > 0) {
								focusedSectionType = section.type
								val sorted = sections.sortedBy { it.order }.toMutableList()
								val currentOrder = sorted[index].order
								val previousOrder = sorted[index - 1].order
								
								sorted[index] = sorted[index].copy(order = previousOrder)
								sorted[index - 1] = sorted[index - 1].copy(order = currentOrder)
								
								saveSections(sorted)
							}
						},
						onMoveDown = {
							if (index < sections.size - 1) {
								focusedSectionType = section.type
								val sorted = sections.sortedBy { it.order }.toMutableList()
								val currentOrder = sorted[index].order
								val nextOrder = sorted[index + 1].order
								
								sorted[index] = sorted[index].copy(order = nextOrder)
								sorted[index + 1] = sorted[index + 1].copy(order = currentOrder)
								
								saveSections(sorted)
							}
						}
					)
				}
			}
		}
		
		item {
			ListButton(
				leadingContent = {
					Icon(
						painterResource(R.drawable.ic_refresh),
						contentDescription = null
					)
				},
				headingContent = { Text(stringResource(R.string.home_sections_reset)) },
				onClick = {
					saveSections(HomeSectionConfig.defaults())
				}
			)
		}
	}
}

@Composable
private fun HomeSectionRow(
	section: HomeSectionConfig,
	canMoveUp: Boolean,
	canMoveDown: Boolean,
	shouldRequestFocus: Boolean,
	onFocusChanged: (Boolean) -> Unit,
	onToggle: () -> Unit,
	onMoveUp: () -> Unit,
	onMoveDown: () -> Unit,
) {
	val context = LocalContext.current
	val focusRequester = remember { FocusRequester() }
	var isFocused by remember { mutableStateOf(false) }
	
	LaunchedEffect(shouldRequestFocus) {
		if (shouldRequestFocus) {
			focusRequester.requestFocus()
		}
	}
	
	ListButton(
		modifier = Modifier
			.fillMaxWidth()
			.focusRequester(focusRequester)
			.onFocusChanged { 
				isFocused = it.isFocused
				onFocusChanged(it.isFocused)
			}
			.onKeyEvent { keyEvent ->
				if (keyEvent.type == KeyEventType.KeyDown) {
					when (keyEvent.key) {
						Key.DirectionLeft -> {
							if (canMoveUp) {
								onMoveUp()
								true
							} else false
						}
						Key.DirectionRight -> {
							if (canMoveDown) {
								onMoveDown()
								true
							} else false
						}
						else -> false
					}
				} else false
			},
		leadingContent = {
			Checkbox(
				checked = section.enabled,
				onCheckedChange = { onToggle() }
			)
		},
		headingContent = {
			Text(context.getString(section.type.nameRes))
		},
		trailingContent = {
			Row(
				horizontalArrangement = Arrangement.spacedBy(4.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Icon(
					painterResource(R.drawable.ic_up),
					contentDescription = null,
					modifier = Modifier.size(24.dp),
					tint = if (canMoveUp && isFocused) Color.White else Color.Gray
				)
				Icon(
					painterResource(R.drawable.ic_down),
					contentDescription = null,
					modifier = Modifier.size(24.dp),
					tint = if (canMoveDown && isFocused) Color.White else Color.Gray
				)
			}
		},
		onClick = onToggle
	)
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
