package org.moonfin.server.emby.api

import org.moonfin.server.core.api.ServerDisplayPreferencesApi
import org.moonfin.server.core.model.DisplayPreferences
import org.moonfin.server.emby.EmbyApiClient
import org.moonfin.server.emby.mapper.toCoreDisplayPreferences
import org.moonfin.server.emby.mapper.toEmbyDisplayPreferences

class EmbyDisplayPreferencesApi(private val apiClient: EmbyApiClient) : ServerDisplayPreferencesApi {

    private val displayPreferences get() = apiClient.displayPreferencesService!!

    override suspend fun getDisplayPreferences(id: String, userId: String, client: String): DisplayPreferences {
        return displayPreferences.getDisplaypreferencesById(id = id, userId = userId, client = client)
            .body()
            .toCoreDisplayPreferences()
    }

    override suspend fun saveDisplayPreferences(id: String, userId: String, prefs: DisplayPreferences) {
        displayPreferences.postDisplaypreferencesByDisplaypreferencesid(
            displayPreferencesId = id,
            userId = userId,
            displayPreferences = prefs.toEmbyDisplayPreferences(),
        )
    }
}
