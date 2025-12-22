package org.jellyfin.androidtv.preference

import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.ApiClient
import kotlin.collections.set

/**
 * Repository to access special preference stores.
 */
class PreferencesRepository(
	private val api: ApiClient,
	private val liveTvPreferences: LiveTvPreferences,
	private val userSettingPreferences: UserSettingPreferences,
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
		// UserSettingPreferences now uses SharedPreferenceStore (local storage) so no server sync needed

		libraryPreferences.clear()
	}
}
