package org.jellyfin.androidtv.preference

import kotlinx.coroutines.runBlocking
import org.jellyfin.androidtv.data.service.pluginsync.PluginSyncService
import org.jellyfin.sdk.api.client.ApiClient
import kotlin.collections.set

/**
 * Repository to access special preference stores.
 */
class PreferencesRepository(
	private val api: ApiClient,
	private val liveTvPreferences: LiveTvPreferences,
	private val userSettingPreferences: UserSettingPreferences,
	private val pluginSyncService: PluginSyncService,
) {
	private val libraryPreferences = mutableMapOf<String, LibraryPreferences>()

	fun getLibraryPreferences(preferencesId: String): LibraryPreferences =
		getLibraryPreferences(preferencesId, api)

	fun getLibraryPreferences(preferencesId: String, apiClient: ApiClient): LibraryPreferences {
		val key = "${apiClient.baseUrl}_$preferencesId"
		val store = libraryPreferences[key] ?: LibraryPreferences(preferencesId, apiClient)

		libraryPreferences[key] = store

		// FIXME: Make [getLibraryPreferences] suspended when usages are converted to Kotlin
		if (store.shouldUpdate) runBlocking { store.update() }

		return store
	}

	suspend fun onSessionChanged() {
		// Note: Do not run parallel as the server can't deal with that
		// Relevant server issue: https://github.com/jellyfin/jellyfin/issues/5261
		liveTvPreferences.update()

		libraryPreferences.clear()

		pluginSyncService.syncOnStartup()
	}

	/**
	 * Configure Jellyseerr proxy via Moonfin plugin.
	 * Must be called AFTER [onSessionChanged] and after the current user is published,
	 * because [configureWithMoonfin] needs the active user for cookie storage isolation.
	 */
	suspend fun configureJellyseerr() {
		pluginSyncService.configureJellyseerrProxy()
	}
}
