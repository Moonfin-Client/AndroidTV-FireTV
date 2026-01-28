package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.model.api.BaseItemDto

@Serializable
data class MdbListRating(
	val source: String,
	val value: Float?,
	val score: Float? = null,
	val votes: Int? = null,
	val popular: Int? = null,
	val url: String? = null
)

@Serializable
data class MdbListResponse(
	val response: Boolean? = null,
	val error: String? = null,
	val id: Int? = null,
	val title: String? = null,
	val year: Int? = null,
	val ratings: List<MdbListRating>? = null
)

class MdbListRepository(private val okHttpClient: OkHttpClient) {
	private val ratingsCache = mutableMapOf<String, Map<String, Float>>()
	private val pendingRequests = mutableMapOf<String, CompletableDeferred<Map<String, Float>?>>()
	
	private val json = Json {
		ignoreUnknownKeys = true
		isLenient = true
	}
	
	suspend fun getRatings(item: BaseItemDto, apiKey: String): Map<String, Float>? = withContext(Dispatchers.IO) {
		if (apiKey.isBlank()) return@withContext null
		
		val tmdbId = item.providerIds?.get("Tmdb")
		val imdbId = item.providerIds?.get("Imdb")
		
		val (paramName, id) = when {
			imdbId != null -> "i" to imdbId
			tmdbId != null -> "tm" to tmdbId
			else -> return@withContext null
		}
		
		val cacheKey = "$paramName:$id"
		
		ratingsCache[cacheKey]?.let { return@withContext it }
		pendingRequests[cacheKey]?.let { return@withContext it.await() }
		
		val deferred = CompletableDeferred<Map<String, Float>?>()
		pendingRequests[cacheKey] = deferred
		
		try {
			val url = "https://mdblist.com/api/?apikey=$apiKey&$paramName=$id"
			val request = Request.Builder().url(url).build()
			val response = okHttpClient.newCall(request).execute()
			
			if (response.isSuccessful) {
				val body = response.body?.string()
				if (body != null) {
					try {
						val mdbListResponse = json.decodeFromString<MdbListResponse>(body)
						
						if (mdbListResponse.response == false || mdbListResponse.error != null) {
							deferred.complete(null)
							pendingRequests.remove(cacheKey)
							return@withContext null
						}
						
						val ratingsMap = mdbListResponse.ratings
							?.mapNotNull { rating -> rating.value?.takeIf { it > 0f }?.let { rating.source.lowercase() to it } }
							?.toMap()
							?: emptyMap()
						
						ratingsCache[cacheKey] = ratingsMap
						deferred.complete(ratingsMap)
						pendingRequests.remove(cacheKey)
						return@withContext ratingsMap
					} catch (e: Exception) {
						deferred.complete(null)
						pendingRequests.remove(cacheKey)
						return@withContext null
					}
				}
			}
			deferred.complete(null)
			pendingRequests.remove(cacheKey)
			return@withContext null
		} catch (e: Exception) {
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
