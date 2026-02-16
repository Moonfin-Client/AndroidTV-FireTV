package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jellyfin.androidtv.data.service.pluginsync.PluginSyncService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.NavbarPosition
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import java.util.UUID

/**
 * Observes [NavbarPosition] and re-reads it when settings close or plugin sync completes.
 */
@Composable
fun rememberNavbarPosition(): NavbarPosition {
	val userPreferences = koinInject<UserPreferences>()
	val settingsClosedCounter by koinActivityViewModel<SettingsViewModel>().settingsClosedCounter.collectAsState()
	val syncCompletedCounter by koinInject<PluginSyncService>().syncCompletedCounter.collectAsState()

	var position by remember { mutableStateOf(userPreferences[UserPreferences.navbarPosition]) }
	LaunchedEffect(settingsClosedCounter, syncCompletedCounter) {
		position = userPreferences[UserPreferences.navbarPosition]
	}
	return position
}

@Composable
fun NavigationLayout(
	activeButton: MainToolbarActiveButton,
	activeLibraryId: UUID? = null,
	content: @Composable () -> Unit
) {
	when (rememberNavbarPosition()) {
		NavbarPosition.LEFT -> {
			Box(modifier = Modifier.fillMaxSize()) {
				content()
				
				LeftSidebarNavigation(
					activeButton = activeButton,
					activeLibraryId = activeLibraryId
				)
			}
		}
		NavbarPosition.TOP -> {
			Column(modifier = Modifier.fillMaxSize()) {
				MainToolbar(
					activeButton = activeButton,
					activeLibraryId = activeLibraryId
				)
				content()
			}
		}
	}
}

/**
 * Shows just the navigation (sidebar or toolbar) without wrapping content.
 * Used for XML-based layouts that can't be wrapped in Compose.
 */
@Composable
fun NavigationOverlay(
	activeButton: MainToolbarActiveButton,
	activeLibraryId: UUID? = null
) {
	when (rememberNavbarPosition()) {
		NavbarPosition.LEFT -> {
			LeftSidebarNavigation(
				activeButton = activeButton,
				activeLibraryId = activeLibraryId
			)
		}
		NavbarPosition.TOP -> {
			MainToolbar(
				activeButton = activeButton,
				activeLibraryId = activeLibraryId
			)
		}
	}
}
