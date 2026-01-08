package org.jellyfin.androidtv.ui.browsing

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.navigation.ProvideRouter
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsDialog
import org.jellyfin.androidtv.ui.settings.composable.SettingsRouterContent
import org.jellyfin.androidtv.ui.settings.routes
import org.koin.core.context.GlobalContext
import java.util.UUID

fun BrowseGridFragment.createSettingsVisibility() = MutableStateFlow(false)

fun BrowseGridFragment.addSettings(
	view: ComposeView,
	itemId: UUID,
	displayPreferencesId: String,
	visible: MutableStateFlow<Boolean>,
) {
	view.setContent {
		val isVisible by visible.collectAsState(false)
		
		// Get current session info
		val sessionRepository = GlobalContext.get().get<SessionRepository>()
		val currentSession by sessionRepository.currentSession.collectAsState()

		JellyfinTheme {
			ProvideRouter(
				routes,
				Routes.LIBRARIES_DISPLAY,
				mapOf(
					"itemId" to itemId.toString(),
					"displayPreferencesId" to displayPreferencesId,
					"serverId" to (currentSession?.serverId?.toString() ?: UUID(0, 0).toString()),
					"userId" to (currentSession?.userId?.toString() ?: UUID(0, 0).toString())
				)
			) {
				SettingsDialog(
					visible = isVisible,
					onDismissRequest = {
						visible.value = false
						onResume()
					}
				) {
					SettingsRouterContent()
				}
			}
		}
	}
}
