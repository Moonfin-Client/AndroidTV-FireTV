package org.jellyfin.androidtv.ui.settings.screen.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.di.defaultDeviceInfo
import org.jellyfin.androidtv.preference.LibraryPreferences
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.util.sdk.forUser
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.DeviceInfo
import org.koin.compose.koinInject
import java.util.UUID

/**
 * Composable helper to load library preferences for a specific server/user context.
 * This is needed for multi-server support where preferences must be saved to the correct server.
 */
@Composable
fun rememberLibraryPreferences(
	displayPreferencesId: String,
	serverId: UUID,
	userId: UUID
): LibraryPreferences? {
	val preferencesRepository = koinInject<PreferencesRepository>()
	val serverRepository = koinInject<ServerRepository>()
	val authenticationStore = koinInject<AuthenticationStore>()
	val jellyfin = koinInject<Jellyfin>()
	val deviceInfo = koinInject<DeviceInfo>(defaultDeviceInfo)
	val currentApi = koinInject<ApiClient>()
	
	var libraryPreferences by remember { mutableStateOf<LibraryPreferences?>(null) }
	
	LaunchedEffect(displayPreferencesId, serverId, userId) {
		val server = serverRepository.getServer(serverId)
		val serverStore = authenticationStore.getServer(serverId)
		val userInfo = serverStore?.users?.get(userId)
		
		libraryPreferences = if (server != null && userInfo != null && !userInfo.accessToken.isNullOrBlank()) {
			val userDeviceInfo = deviceInfo.forUser(userId)
			val apiClient = jellyfin.createApi(
				baseUrl = server.address,
				accessToken = userInfo.accessToken,
				deviceInfo = userDeviceInfo
			)
			preferencesRepository.getLibraryPreferences(displayPreferencesId, apiClient)
		} else {
			// Fallback to current session's API client
			preferencesRepository.getLibraryPreferences(displayPreferencesId, currentApi)
		}
	}
	
	return libraryPreferences
}
