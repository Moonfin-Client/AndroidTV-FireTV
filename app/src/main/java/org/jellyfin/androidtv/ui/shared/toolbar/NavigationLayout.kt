package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.NavbarPosition
import org.koin.compose.koinInject
import java.util.UUID

/**
 * A composable that wraps content with either a left sidebar or top toolbar
 * based on the user's navbar position preference.
 */
@Composable
fun NavigationLayout(
	activeButton: MainToolbarActiveButton,
	activeLibraryId: UUID? = null,
	content: @Composable () -> Unit
) {
	val userPreferences = koinInject<UserPreferences>()
	val navbarPosition = userPreferences[UserPreferences.navbarPosition]
	
	when (navbarPosition) {
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
 * A composable that shows just the navigation (sidebar or toolbar) without wrapping content.
 * Used for XML-based layouts that can't be wrapped in Compose.
 */
@Composable
fun NavigationOverlay(
	activeButton: MainToolbarActiveButton,
	activeLibraryId: UUID? = null
) {
	val userPreferences = koinInject<UserPreferences>()
	val navbarPosition = userPreferences[UserPreferences.navbarPosition]
	
	when (navbarPosition) {
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
