package org.moonfin.server.emby.api

import org.moonfin.server.core.api.ServerDisplayPreferencesApi
import org.moonfin.server.core.model.DisplayPreferences
import org.moonfin.server.emby.EmbyApiClient
import org.moonfin.server.emby.mapper.toCoreDisplayPreferences
import org.moonfin.server.emby.mapper.toEmbyDisplayPreferences

class EmbyDisplayPreferencesApi(private val apiClient: EmbyApiClient) : ServerDisplayPreferencesApi {

    private val displayPreferences get() = apiClient.displayPreferencesService!!

    private data class CacheKey(val id: String, val userId: String, val client: String)
    private data class CacheEntry(val prefs: DisplayPreferences, val timestamp: Long)

    private val cache = mutableMapOf<CacheKey, CacheEntry>()

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
    }

    override suspend fun getDisplayPreferences(id: String, userId: String, client: String): DisplayPreferences {
        val key = CacheKey(id, userId, client)
        val now = System.currentTimeMillis()
        val entry = cache[key]
        if (entry != null && now - entry.timestamp < CACHE_TTL_MS) {
            return entry.prefs
        }

        val prefs = displayPreferences.getDisplaypreferencesById(id = id, userId = userId, client = client)
            .body()
            .toCoreDisplayPreferences()

        cache[key] = CacheEntry(prefs, now)
        return prefs
    }

    override suspend fun saveDisplayPreferences(id: String, userId: String, prefs: DisplayPreferences) {
        displayPreferences.postDisplaypreferencesByDisplaypreferencesid(
            displayPreferencesId = id,
            userId = userId,
            displayPreferences = prefs.toEmbyDisplayPreferences(),
        )
        cache[CacheKey(id, userId, "emby")] = CacheEntry(prefs, System.currentTimeMillis())
    }

    fun invalidateCache() {
        cache.clear()
    }
}
