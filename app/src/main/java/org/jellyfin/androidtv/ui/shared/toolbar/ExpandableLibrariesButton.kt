package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.Session
import org.jellyfin.androidtv.data.model.AggregatedLibrary
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonColors
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

/**
 * A single button that shows a Libraries icon and expands to show all available libraries when focused.
 */
@Composable
fun ExpandableLibrariesButton(
	activeLibraryId: UUID?,
	userViews: List<BaseItemDto>,
	aggregatedLibraries: List<AggregatedLibrary>,
	enableMultiServer: Boolean,
	currentSession: Session?,
	colors: ButtonColors,
	activeColors: ButtonColors,
	navigationRepository: NavigationRepository,
	itemLauncher: ItemLauncher,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val scope = rememberCoroutineScope()
	
	// Track focus within the entire group (icon + expanded libraries)
	var hasFocusInGroup by remember { mutableStateOf(false) }

	// Check if any library is active
	val hasActiveLibrary = activeLibraryId != null

	Box(
		modifier = Modifier.onFocusChanged { focusState ->
			hasFocusInGroup = focusState.hasFocus
		}
	) {
		Row(
			modifier = Modifier.focusGroup(),
			horizontalArrangement = Arrangement.spacedBy(0.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(
				onClick = {},
				colors = if (hasActiveLibrary) activeColors else colors,
				interactionSource = interactionSource,
			) {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.ic_clapperboard),
					contentDescription = "Libraries",
				)
			}
			
			AnimatedVisibility(
				visible = hasFocusInGroup,
				enter = expandHorizontally(
					expandFrom = Alignment.Start,
					animationSpec = tween(durationMillis = 250)
				) + fadeIn(animationSpec = tween(durationMillis = 250)),
				exit = shrinkHorizontally(
					shrinkTowards = Alignment.Start,
					animationSpec = tween(durationMillis = 200)
				) + fadeOut(animationSpec = tween(durationMillis = 200)),
			) {
				Row(
					horizontalArrangement = Arrangement.spacedBy(4.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Spacer(modifier = Modifier.width(8.dp))
					
					ProvideTextStyle(
						JellyfinTheme.typography.default.copy(fontWeight = FontWeight.Bold)
					) {
						if (enableMultiServer && aggregatedLibraries.isNotEmpty()) {
							aggregatedLibraries.forEach { aggLib ->
								val isActiveLibrary = activeLibraryId == aggLib.library.id
								
								Button(
									onClick = {
										if (!isActiveLibrary) {
											scope.launch {
												val destination = Destinations.libraryBrowser(aggLib.library, aggLib.server.id)
												navigationRepository.navigate(destination)
											}
										}
									},
									colors = if (isActiveLibrary) activeColors else colors,
								) {
									Text(aggLib.displayName)
								}
							}
						} else {
							userViews.forEach { library ->
								val isActiveLibrary = activeLibraryId == library.id
								
								Button(
									onClick = {
										if (!isActiveLibrary) {
											val destination = itemLauncher.getUserViewDestination(library)
											navigationRepository.navigate(destination)
										}
									},
									colors = if (isActiveLibrary) activeColors else colors,
								) {
									Text(library.name ?: "")
								}
							}
						}
					}
					
					Spacer(modifier = Modifier.width(4.dp))
				}
			}
		}
	}
}
