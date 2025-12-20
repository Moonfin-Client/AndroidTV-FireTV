package org.jellyfin.androidtv.ui.preference.screen

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
fun HomeSectionsConfigScreen() {
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	
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
				overlineContent = { Text(stringResource(R.string.home_prefs).uppercase()) },
				headingContent = { Text(stringResource(R.string.home_sections)) },
				captionContent = { Text(stringResource(R.string.home_sections_description)) },
			)
		}
		
		// Display each section with checkbox and reorder buttons
		// Filter out MEDIA_BAR since it's controlled by a separate toggle in Moonfin settings
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
	
	// Request focus when this item should be focused
	androidx.compose.runtime.LaunchedEffect(shouldRequestFocus) {
		if (shouldRequestFocus) {
			focusRequester.requestFocus()
		}
	}
	
	// Single button for the entire row - left/right keys move the item up/down
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
			// Visual indicators for up/down (not interactive)
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
	)}