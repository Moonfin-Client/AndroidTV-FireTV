package org.moonfin.server.core.api

import org.moonfin.server.core.model.DisplayPreferences

interface ServerDisplayPreferencesApi {
    suspend fun getDisplayPreferences(id: String, userId: String, client: String): DisplayPreferences
    suspend fun saveDisplayPreferences(id: String, userId: String, prefs: DisplayPreferences)
}
