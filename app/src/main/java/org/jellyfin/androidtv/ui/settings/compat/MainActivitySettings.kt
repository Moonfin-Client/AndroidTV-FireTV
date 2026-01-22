package org.jellyfin.androidtv.ui.settings.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.home.HomeFragment
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.navigation.ProvideRouter
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsDialog
import org.jellyfin.androidtv.ui.settings.composable.SettingsRouterContent
import org.jellyfin.androidtv.ui.settings.routes
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun MainActivitySettings() {
	val viewModel = koinActivityViewModel<SettingsViewModel>()
	val visible by viewModel.visible.collectAsState()
	val navigationRepository = koinInject<NavigationRepository>()

	JellyfinTheme {
		ProvideRouter(routes, Routes.MAIN) {
			SettingsDialog(
				visible = visible,
				onDismissRequest = {
					viewModel.hide()
					// Reload home if user is on the home screen to apply any settings changes
					val currentDestination = navigationRepository.currentDestination
					if (currentDestination?.fragment == HomeFragment::class) {
						navigationRepository.reset(Destinations.home)
					}
				}
			) {
				SettingsRouterContent()
			}
		}
	}
}
