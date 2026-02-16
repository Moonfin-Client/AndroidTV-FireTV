package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

/**
 * Rating entry returned by the Moonfin plugin's `/Moonfin/MdbList/Ratings` endpoint.
 */
@Serializable
data class MdbListRating(
	val source: String? = null,
	val value: Float? = null,
	val score: Float? = null,
	val votes: Int? = null,
	val url: String? = null,
)

/**
 * Response wrapper from the Moonfin plugin MDBList proxy.
 */
@Serializable
data class MdbListResponse(
	val success: Boolean = false,
	val error: String? = null,
	val ratings: List<MdbListRating>? = null,
)

/**
 * Fetches MDBList ratings via the Moonfin server plugin proxy.
 *
 * Endpoint: `GET {serverUrl}/Moonfin/MdbList/Ratings?type={movie|show}&tmdbId={id}`
 * Auth: `Authorization: MediaBrowser Token="{accessToken}"`
 *
 * The API key is managed server-side — no client-side key is needed.
 */
class MdbListRepository(
	private val okHttpClient: OkHttpClient,
	private val apiClient: ApiClient,
) {
	private val ratingsCache = mutableMapOf<String, Map<String, Float>>()
	private val pendingRequests = mutableMapOf<String, CompletableDeferred<Map<String, Float>?>>()

	private val json = Json {
		ignoreUnknownKeys = true
		isLenient = true
	}

	/**
	 * Fetch ratings for [item] from the Moonfin MDBList plugin proxy.
	 * Returns a map of source name (lowercase) → rating value, or null on failure.
	 */
	suspend fun getRatings(item: BaseItemDto): Map<String, Float>? = withContext(Dispatchers.IO) {
		val tmdbId = item.providerIds?.get("Tmdb") ?: return@withContext null

		val type = when (item.type) {
			BaseItemKind.MOVIE -> "movie"
			BaseItemKind.SERIES -> "show"
			BaseItemKind.EPISODE, BaseItemKind.SEASON -> "show"
			else -> "movie" // fallback for media bar items constructed as MOVIE
		}

		val cacheKey = "$type:$tmdbId"

		ratingsCache[cacheKey]?.let { return@withContext it }
		pendingRequests[cacheKey]?.let { return@withContext it.await() }

		val deferred = CompletableDeferred<Map<String, Float>?>()
		pendingRequests[cacheKey] = deferred

		try {
			val baseUrl = apiClient.baseUrl ?: run {
				Timber.w("MdbListRepository: No server URL available")
				deferred.complete(null)
				pendingRequests.remove(cacheKey)
				return@withContext null
			}
			val accessToken = apiClient.accessToken ?: run {
				Timber.w("MdbListRepository: No access token available")
				deferred.complete(null)
				pendingRequests.remove(cacheKey)
				return@withContext null
			}

			val url = "$baseUrl/Moonfin/MdbList/Ratings?type=$type&tmdbId=$tmdbId"
			Timber.d("MdbListRepository: Fetching ratings from plugin: $url")

			val request = Request.Builder()
				.url(url)
				.addHeader("Authorization", "MediaBrowser Token=\"$accessToken\"")
				.build()
			val response = okHttpClient.newCall(request).execute()

			if (response.isSuccessful) {
				val body = response.body?.string()
				if (body != null) {
					try {
						val pluginResponse = json.decodeFromString<MdbListResponse>(body)

						if (!pluginResponse.success || pluginResponse.error != null) {
							Timber.w("MdbListRepository: Plugin returned error: ${pluginResponse.error}")
							deferred.complete(null)
							pendingRequests.remove(cacheKey)
							return@withContext null
						}

						val ratingsMap = pluginResponse.ratings
							?.mapNotNull { rating ->
								val source = rating.source?.lowercase() ?: return@mapNotNull null
								// metacriticuser: native value is 0-10 but display code expects 0-100.
								// Use score (0-100 normalized) for correct display, matching web plugin.
								// For all others: prefer native value, fall back to score.
								val ratingValue = when (source) {
									"metacriticuser" -> (rating.score ?: rating.value)?.takeIf { it > 0f }
									else -> (rating.value ?: rating.score)?.takeIf { it > 0f }
								}
								ratingValue?.let { source to it }
							}
							?.toMap(LinkedHashMap())
							?: linkedMapOf()

						ratingsCache[cacheKey] = ratingsMap
						deferred.complete(ratingsMap)
						pendingRequests.remove(cacheKey)
						return@withContext ratingsMap
					} catch (e: Exception) {
						Timber.w(e, "MdbListRepository: Failed to parse plugin response")
						deferred.complete(null)
						pendingRequests.remove(cacheKey)
						return@withContext null
					}
				}
			} else {
				Timber.w("MdbListRepository: Plugin request failed: ${response.code} ${response.message}")
			}
			deferred.complete(null)
			pendingRequests.remove(cacheKey)
			return@withContext null
		} catch (e: Exception) {
			Timber.e(e, "MdbListRepository: Error fetching ratings from plugin")
			deferred.complete(null)
			pendingRequests.remove(cacheKey)
			return@withContext null
		}
	}

	fun clearCache() {
		ratingsCache.clear()
		pendingRequests.clear()
	}
}
