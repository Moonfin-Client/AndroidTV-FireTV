package org.jellyfin.androidtv.data.service.jellyseerr

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Exception thrown when Jellyseerr returns a 403 Forbidden error,
 * typically indicating session expiration or permission issues.
 */
class JellyseerrSessionExpiredException(
	val statusCode: Int,
	val errorBody: String,
	message: String = "Jellyseerr session expired or permission denied"
) : Exception(message)

/**
 * HTTP client for communicating with Jellyseerr API
 */
class JellyseerrHttpClient(
	context: android.content.Context,
	private val baseUrl: String,
	private val apiKey: String,
) {
	companion object {
		private const val REQUEST_TIMEOUT_SECONDS = 30L
		private const val JELLYSEERR_API_VERSION = "v1"
		
		// Delegating cookie storage that can switch between users
		private var cookieStorage: DelegatingCookiesStorage? = null
		private var appContext: android.content.Context? = null
		
		fun initializeCookieStorage(context: android.content.Context) {
			appContext = context.applicationContext
			if (cookieStorage == null) {
				cookieStorage = DelegatingCookiesStorage(context.applicationContext)
			}
		}

		/**
		 * Switch cookie storage to a different user
		 * Each user gets their own cookie storage to maintain separate Jellyseerr sessions
		 */
		fun switchCookieStorage(userId: String) {
			cookieStorage?.switchToUser(userId)
			Timber.d("Jellyseerr: Switched cookie storage to user: $userId")
		}

		/**
		 * Clear all stored cookies (e.g., for logout)
		 */
		suspend fun clearCookies() {
			cookieStorage?.clearAll()
		}
	}
	
	/**
	 * Helper function to add API key header if available
	 * Falls back to cookie-based authentication if API key is empty
	 */
	private fun HttpRequestBuilder.addAuthHeader() {
		if (apiKey.isNotEmpty()) {
			header("X-Api-Key", apiKey)
		}
		// If apiKey is empty, rely on session cookies for authentication
	}

	private val jsonConfig = Json {
		ignoreUnknownKeys = true
		prettyPrint = false
		encodeDefaults = false
		coerceInputValues = true
	}

	/**
	 * Check response for 403 errors and throw JellyseerrSessionExpiredException if found.
	 * This allows callers to detect session expiration and trigger re-authentication.
	 */
	private suspend fun checkResponseAndThrowOn403(
		response: io.ktor.client.statement.HttpResponse,
		operation: String
	) {
		if (response.status.value == 403) {
			val errorBody = try {
				response.body<String>()
			} catch (e: Exception) {
				"Unable to parse error body"
			}
			Timber.e("Jellyseerr: 403 Forbidden during $operation - Session may have expired. Error: $errorBody")
			throw JellyseerrSessionExpiredException(403, errorBody, "Jellyseerr session expired: $errorBody")
		}
	}

	init {
		// Initialize persistent cookie storage
		initializeCookieStorage(context)
	}

	private val httpClient = HttpClient(OkHttp) {
		install(ContentNegotiation) {
			json(jsonConfig)
		}
		
		install(HttpCookies) {
			// Use delegating cookie storage that can switch between users
			storage = cookieStorage!!
		}

		engine {
			config {
				connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			}
		}
	}

	// ==================== Request Management ====================

	/**
	 * Get all requests visible to the current user
	 */
	suspend fun getRequests(
		sort: String = "updated",
		filter: String? = null,
		requestedBy: Int? = null,
		requestType: String? = null,
		limit: Int = 50,
		offset: Int = 0,
	): Result<JellyseerrListResponse<JellyseerrRequestDto>> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/request").apply {
			parameters.append("skip", offset.toString())
			parameters.append("take", limit.toString())
			// Add filter parameter if provided (valid values: all, approved, available, pending, processing, unavailable, failed, deleted, completed)
			filter?.let { parameters.append("filter", it) }
			// Add requestedBy parameter to filter by user ID
			requestedBy?.let { parameters.append("requestedBy", it.toString()) }
		}.build()

		val response = httpClient.get(url) {
			addAuthHeader()
		}

		Timber.d("Jellyseerr: Got requests - Status: ${response.status}, URL: $url")

		// Check for 403 and throw session expired exception
		checkResponseAndThrowOn403(response, "getRequests")

		if (response.status.value !in 200..299) {
			val errorBody = try {
				response.body<String>()
			} catch (e: Exception) {
				"Unknown error"
			}
			Timber.e("Jellyseerr: Error response body: $errorBody")
			throw Exception("Jellyseerr API error ${response.status}: $errorBody")
		}

		val responseBody = response.body<JellyseerrListResponse<JellyseerrRequestDto>>()
		Timber.d("Jellyseerr: Parsed ${responseBody.results.size} requests")
		responseBody.results.forEach { request ->
			Timber.d("Jellyseerr: Request ${request.id} - Type: ${request.type}, Status: ${request.status}")
			if (request.media != null) {
				Timber.d("Jellyseerr: Media ID: ${request.media.id}, mediaType: ${request.media.mediaType}, tmdbId: ${request.media.tmdbId}")
				Timber.d("Jellyseerr: Media title: '${request.media.title}', name: '${request.media.name}', posterPath: '${request.media.posterPath}'")
			} else {
				Timber.e("Jellyseerr: Request ${request.id} has NULL media object!")
			}
			if (request.requestedBy != null) {
				Timber.d("Jellyseerr: RequestedBy ID: ${request.requestedBy.id}, username: ${request.requestedBy.username}")
			} else {
				Timber.e("Jellyseerr: Request ${request.id} has NULL requestedBy!")
			}
		}
		responseBody
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get requests")
	}

	/**
	 * Get details of a specific request
	 */
	suspend fun getRequest(requestId: Int): Result<JellyseerrRequestDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/request/$requestId").build()
		val response = httpClient.get(url) {
			addAuthHeader()
		}
		Timber.d("Jellyseerr: Got request $requestId - Status: ${response.status}")
		response.body<JellyseerrRequestDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get request $requestId")
	}

	/**
	 * Create a new request for a movie or TV show
	 */
	suspend fun createRequest(
		mediaId: Int,
		mediaType: String,
		seasons: Seasons? = null,
		is4k: Boolean = false,
		profileId: Int? = null,
		rootFolderId: Int? = null,
		serverId: Int? = null,
	): Result<JellyseerrRequestDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/request").build()
		
		// For TV shows, default to "all" seasons if not specified
		val seasonsValue = if (mediaType == "tv" && seasons == null) {
			Seasons.All
		} else {
			seasons
		}
		
		val requestBody = JellyseerrCreateRequestDto(
			mediaId = mediaId,
			mediaType = mediaType,
			seasons = seasonsValue,
			is4k = is4k,
			profileId = profileId,
			rootFolderId = rootFolderId,
			serverId = serverId,
		)

		val response = httpClient.post(url) {
			addAuthHeader()
			contentType(ContentType.Application.Json)
			setBody(requestBody)
		}

		Timber.d("Jellyseerr: Created request for $mediaType:$mediaId (4K=$is4k, profileId=$profileId) - Status: ${response.status}")
		
		// Check if request was successful
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: Request failed with status ${response.status}: $errorBody")
			throw Exception("Failed to create request: ${response.status} - $errorBody")
		}
		
		response.body<JellyseerrRequestDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to create request for $mediaType:$mediaId")
	}

	/**
	 * Delete an existing request
	 */
	suspend fun deleteRequest(requestId: Int): Result<Unit> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/request/$requestId").build()
		
		val response = httpClient.delete(url) {
			addAuthHeader()
		}

		Timber.d("Jellyseerr: Deleted request $requestId - Status: ${response.status}")
		
		// Check if request was successful
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: Delete request failed with status ${response.status}: $errorBody")
			throw Exception("Failed to delete request: ${response.status} - $errorBody")
		}
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to delete request $requestId")
	}

	// ==================== Discover Content ====================

	/**
	 * Get trending content (movies and TV combined)
	 */
	suspend fun getTrending(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/discover/trending").apply {
			parameters.append("page", ((offset / limit) + 1).toString())
			parameters.append("language", "en")
		}.build()

		val response = httpClient.get(url) {
			addAuthHeader()
		}

		// Check for 403 and throw session expired exception
		checkResponseAndThrowOn403(response, "getTrending")

		val body = response.body<JellyseerrDiscoverPageDto>()
		Timber.d("Jellyseerr: Got trending content - Status: ${response.status}, Count: ${body.results?.size ?: 0}")
		body
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get trending content")
	}

	/**
	 * Get trending movies
	 */
	suspend fun getTrendingMovies(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/discover/movies").apply {
			parameters.append("page", ((offset / limit) + 1).toString())
			parameters.append("language", "en")
		}.build()

		val response = httpClient.get(url) {
			addAuthHeader()
		}

		// Check for 403 and throw session expired exception
		checkResponseAndThrowOn403(response, "getTrendingMovies")

		val body = response.body<JellyseerrDiscoverPageDto>()
		Timber.d("Jellyseerr: Got trending movies - Status: ${response.status}, Count: ${body.results?.size ?: 0}")
		if (!body.results.isNullOrEmpty()) {
			val mediaTypes = body.results.map { it.mediaType }.distinct()
			Timber.d("Jellyseerr: Movies endpoint returned media types: $mediaTypes")
			body.results.take(3).forEach { item ->
				Timber.d("Jellyseerr: Movie item - Title: ${item.title ?: item.name}, MediaType: ${item.mediaType}")
			}
		}

		body
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get trending movies")
	}

	/**
	 * Get trending TV shows
	 */
	suspend fun getTrendingTv(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/discover/tv").apply {
			parameters.append("page", ((offset / limit) + 1).toString())
			parameters.append("language", "en")
		}.build()

		val response = httpClient.get(url) {
			addAuthHeader()
		}

		// Check for 403 and throw session expired exception
		checkResponseAndThrowOn403(response, "getTrendingTv")

		val body = response.body<JellyseerrDiscoverPageDto>()
		Timber.d("Jellyseerr: Got trending TV - Status: ${response.status}, Count: ${body.results?.size ?: 0}")
		if (!body.results.isNullOrEmpty()) {
			val mediaTypes = body.results.map { it.mediaType }.distinct()
			Timber.d("Jellyseerr: TV endpoint returned media types: $mediaTypes")
			body.results.take(3).forEach { item ->
				Timber.d("Jellyseerr: TV item - Title: ${item.title ?: item.name}, MediaType: ${item.mediaType}")
			}
		}

		body
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get trending TV shows")
	}

	/**
	 * Get top-rated movies
	 */
	suspend fun getTopMovies(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/discover/movies/top").apply {
			parameters.append("limit", limit.toString())
			parameters.append("offset", offset.toString())
		}.build()

		val response = httpClient.get(url) {
			addAuthHeader()
		}

		// Check for 403 and throw session expired exception
		checkResponseAndThrowOn403(response, "getTopMovies")

		Timber.d("Jellyseerr: Got top movies - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get top movies")
	}

	/**
	 * Get top-rated TV shows
	 */
	suspend fun getTopTv(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/discover/tv/top").apply {
			parameters.append("limit", limit.toString())
			parameters.append("offset", offset.toString())
		}.build()

		val response = httpClient.get(url) {
			addAuthHeader()
		}

		// Check for 403 and throw session expired exception
		checkResponseAndThrowOn403(response, "getTopTv")

		Timber.d("Jellyseerr: Got top TV shows - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get top TV shows")
	}

	/**
	 * Get upcoming movies
	 */
	suspend fun getUpcomingMovies(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/discover/movies/upcoming").apply {
		}.build()

		val response = httpClient.get(url) {
			addAuthHeader()
		}

		// Check for 403 and throw session expired exception
		checkResponseAndThrowOn403(response, "getUpcomingMovies")

		Timber.d("Jellyseerr: Got upcoming movies - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get upcoming movies")
	}

	/**
	 * Get upcoming TV shows
	 */
	suspend fun getUpcomingTv(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/discover/tv/upcoming").apply {
		}.build()

		val response = httpClient.get(url) {
			addAuthHeader()
		}

		// Check for 403 and throw session expired exception
		checkResponseAndThrowOn403(response, "getUpcomingTv")

		Timber.d("Jellyseerr: Got upcoming TV shows - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get upcoming TV shows")
	}

	/**
	 * Search for movies or TV shows
	 */
	suspend fun search(
		query: String,
		mediaType: String? = null,
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto> = runCatching {
		// URLEncoder uses '+' for spaces, but Jellyseerr expects '%20'
		val encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
		val page = ((offset / limit) + 1).toString()

		// Build URL with manually encoded query parameter
		val url = buildString {
			append("$baseUrl/api/v1/search")
			append("?query=$encodedQuery")
			append("&page=$page")
			if (mediaType != null) {
				val encodedType = URLEncoder.encode(mediaType, "UTF-8").replace("+", "%20")
				append("&type=$encodedType")
			}
		}

		val response = httpClient.get(url) {
			addAuthHeader()
		}

		// Check for 403 and throw session expired exception
		checkResponseAndThrowOn403(response, "search")

		Timber.d("Jellyseerr: Searched for '$query' - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to search for '$query'")
	}

	/**
	 * Get similar movies for a given movie ID
	 */
	suspend fun getSimilarMovies(tmdbId: Int, page: Int = 1): Result<JellyseerrDiscoverPageDto> = runCatching {
		val url = "$baseUrl/api/v1/movie/$tmdbId/similar"
		val response = httpClient.get(url) {
			addAuthHeader()
			url {
				parameters.append("page", page.toString())
			}
		}

		Timber.d("Jellyseerr: Got similar movies for movie $tmdbId - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get similar movies for movie $tmdbId")
	}

	/**
	 * Get similar TV shows for a given TV show ID
	 */
	suspend fun getSimilarTv(tmdbId: Int, page: Int = 1): Result<JellyseerrDiscoverPageDto> = runCatching {
		val url = "$baseUrl/api/v1/tv/$tmdbId/similar"
		val response = httpClient.get(url) {
			addAuthHeader()
			url {
				parameters.append("page", page.toString())
			}
		}

		Timber.d("Jellyseerr: Got similar TV shows for TV show $tmdbId - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get similar TV shows for TV show $tmdbId")
	}

	// ==================== Blacklist ====================

	/**
	 * Get blacklisted items
	 */
	suspend fun getBlacklist(): Result<JellyseerrBlacklistPageDto> = runCatching {
		val url = "$baseUrl/api/v1/blacklist"
		val response = httpClient.get(url) {
			addAuthHeader()
		}

		Timber.d("Jellyseerr: Got blacklist - Status: ${response.status}")
		response.body<JellyseerrBlacklistPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get blacklist")
	}

	// ==================== Person ====================

	/**
	 * Get person details by ID
	 */
	suspend fun getPersonDetails(personId: Int): Result<JellyseerrPersonDetailsDto> = runCatching {
		val url = "$baseUrl/api/v1/person/$personId"
		val response = httpClient.get(url) {
			addAuthHeader()
		}

		Timber.d("Jellyseerr: Got person details for person $personId - Status: ${response.status}")
		response.body<JellyseerrPersonDetailsDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get person details for person $personId")
	}

	/**
	 * Get combined credits (movies and TV) for a person
	 */
	suspend fun getPersonCombinedCredits(personId: Int): Result<JellyseerrPersonCombinedCreditsDto> = runCatching {
		val url = "$baseUrl/api/v1/person/$personId/combined_credits"
		val response = httpClient.get(url) {
			addAuthHeader()
		}

		Timber.d("Jellyseerr: Got combined credits for person $personId - Status: ${response.status}")
		response.body<JellyseerrPersonCombinedCreditsDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get combined credits for person $personId")
	}

	// ==================== Media Details ====================

	/**
	 * Get detailed movie information including cast
	 */
	suspend fun getMovieDetails(tmdbId: Int): Result<JellyseerrMovieDetailsDto> = runCatching {
		val url = "$baseUrl/api/v1/movie/$tmdbId"
		val response = httpClient.get(url) {
			addAuthHeader()
		}
		
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: Movie details request failed - Status: ${response.status}, TMDB ID: $tmdbId, Error: $errorBody")
			throw Exception("Failed to fetch movie details: ${response.status} - $errorBody")
		}
		
		Timber.d("Jellyseerr: Got movie details - Status: ${response.status}, TMDB ID: $tmdbId")
		val details = response.body<JellyseerrMovieDetailsDto>()
		Timber.d("Jellyseerr: Movie has ${details.credits?.cast?.size ?: 0} cast members")
		details
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get movie details for TMDB ID $tmdbId")
	}

	/**
	 * Get detailed TV show information including cast
	 */
	suspend fun getTvDetails(tmdbId: Int): Result<JellyseerrTvDetailsDto> = runCatching {
		val url = "$baseUrl/api/v1/tv/$tmdbId"
		val response = httpClient.get(url) {
			addAuthHeader()
		}
		
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: TV details request failed - Status: ${response.status}, TMDB ID: $tmdbId, Error: $errorBody")
			throw Exception("Failed to fetch TV details: ${response.status} - $errorBody")
		}
		
		Timber.d("Jellyseerr: Got TV details - Status: ${response.status}, TMDB ID: $tmdbId")
		val details = response.body<JellyseerrTvDetailsDto>()
		Timber.d("Jellyseerr: TV show has ${details.credits?.cast?.size ?: 0} cast members")
		details
	}.onFailure { error ->
			Timber.e(error, "Jellyseerr: Failed to get TV details for TMDB ID $tmdbId")
	}

	// ==================== User Management ====================

	/**
	 * Login with local credentials and get API key
	 */
	suspend fun loginLocal(email: String, password: String): Result<JellyseerrUserDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/auth/local").build()
		val loginBody = mapOf("email" to email, "password" to password)
		
		val response = httpClient.post(url) {
			contentType(ContentType.Application.Json)
			setBody(loginBody)
		}
		
		Timber.d("Jellyseerr: Local login - Status: ${response.status}")
		
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: Login failed with status ${response.status}: $errorBody")
			throw Exception("Login failed: ${response.status}")
		}
		
		response.body<JellyseerrUserDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to login locally")
	}

	/**
	 * Login with Jellyfin credentials
	 * First attempts without hostname (for already-configured servers)
	 * Falls back to including hostname on 401 (for initial server setup)
	 */
	suspend fun loginJellyfin(username: String, password: String, jellyfinUrl: String): Result<JellyseerrUserDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/auth/jellyfin").build()
		
		// Clear any existing cookies to prevent stale session issues
		clearCookies()
		
		// First attempt: without hostname (standard for already-configured servers)
		var response = httpClient.post(url) {
			contentType(ContentType.Application.Json)
			setBody(mapOf(
				"username" to username,
				"password" to password
			))
		}
		
		// Success - return user
		if (response.status.value in 200..299) {
			return@runCatching response.body<JellyseerrUserDto>()
		}
		
		// 401 - Server not configured yet, retry with hostname
		if (response.status.value == 401) {
			response = httpClient.post(url) {
				contentType(ContentType.Application.Json)
				setBody(mapOf(
					"username" to username,
					"password" to password,
					"hostname" to jellyfinUrl
				))
			}
			
			if (response.status.value in 200..299) {
				return@runCatching response.body<JellyseerrUserDto>()
			}
			
			// Handle errors from second attempt
			val errorBody = response.body<String>()
			throw Exception("Jellyfin login failed: ${response.status} - $errorBody")
		}
		
		// 500 - Likely wrong credentials on configured server
		if (response.status.value == 500) {
			val errorBody = response.body<String>()
			throw Exception("Authentication failed. Verify your username and password are correct, and that the Jellyfin server URL in Jellyseerr settings matches: $jellyfinUrl")
		}
		
		// Other errors
		val errorBody = response.body<String>()
		throw Exception("Jellyfin login failed: ${response.status} - $errorBody")
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to login with Jellyfin")
	}

	/**
	 * Get the current authenticated user
	 */
	suspend fun getCurrentUser(): Result<JellyseerrUserDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/auth/me").build()
		val response = httpClient.get(url) {
			addAuthHeader()
		}
		
		if (response.status.value !in 200..299) {
			throw Exception("Failed to get current user: ${response.status}")
		}
		
		response.body<JellyseerrUserDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get current user")
	}

	/**
	 * Regenerate API key for the current user (requires admin permissions)
	 * Returns the new API key from MainSettings
	 */
	suspend fun regenerateApiKey(): Result<String> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/settings/main/regenerate").build()
		
		val response = httpClient.post(url) {
			header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Safari/537.36")
			header("Origin", baseUrl)
			header("Referer", "$baseUrl/")
		}
		
		if (response.status.value !in 200..299) {
			throw Exception("Failed to regenerate API key (requires admin): ${response.status}")
		}
		
		response.body<JellyseerrMainSettingsDto>().apiKey
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to regenerate API key")
	}

	// ==================== Status & Configuration ====================

	/**
	 * Check if Jellyseerr is available and get status
	 */
	suspend fun getStatus(): Result<JellyseerrStatusDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/status").build()
		val response = httpClient.get(url) {
			addAuthHeader()
		}
		response.body<JellyseerrStatusDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get status")
	}

	/**
	 * Test the connection by checking status
	 */
	suspend fun testConnection(): Result<Boolean> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/status").build()
		val response = httpClient.get(url) {
			addAuthHeader()
		}
		response.status.value in 200..299
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Connection test failed")
	}

	// ==================== Service Configuration ====================

	/**
	 * Get all Radarr server configurations
	 * Returns list of Radarr instances with their profiles and root folders
	 */
	suspend fun getRadarrSettings(): Result<List<JellyseerrRadarrSettingsDto>> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/settings/radarr").build()
		val response = httpClient.get(url) {
			addAuthHeader()
		}
		
		Timber.d("Jellyseerr: Got Radarr settings - Status: ${response.status}")
		
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: getRadarrSettings failed with status ${response.status}: $errorBody")
			throw Exception("Failed to get Radarr settings: ${response.status}")
		}
		
		response.body<List<JellyseerrRadarrSettingsDto>>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get Radarr settings")
	}

	/**
	 * Get all Sonarr server configurations
	 * Returns list of Sonarr instances with their profiles and root folders
	 */
	suspend fun getSonarrSettings(): Result<List<JellyseerrSonarrSettingsDto>> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/settings/sonarr").build()
		val response = httpClient.get(url) {
			addAuthHeader()
		}
		
		Timber.d("Jellyseerr: Got Sonarr settings - Status: ${response.status}")
		
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: getSonarrSettings failed with status ${response.status}: $errorBody")
			throw Exception("Failed to get Sonarr settings: ${response.status}")
		}
		
		response.body<List<JellyseerrSonarrSettingsDto>>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get Sonarr settings")
	}

	fun close() {
		httpClient.close()
	}
}
