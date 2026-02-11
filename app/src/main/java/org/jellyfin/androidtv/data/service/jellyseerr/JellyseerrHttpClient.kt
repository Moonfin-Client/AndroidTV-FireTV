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
		
		private var cookieStorage: DelegatingCookiesStorage? = null
		private var appContext: android.content.Context? = null
		
		fun initializeCookieStorage(context: android.content.Context) {
			appContext = context.applicationContext
			if (cookieStorage == null) {
				cookieStorage = DelegatingCookiesStorage(context.applicationContext)
			}
		}

		fun switchCookieStorage(userId: String) {
			cookieStorage?.switchToUser(userId)
			Timber.d("Jellyseerr: Switched cookie storage to user: $userId")
		}

		suspend fun clearCookies() {
			cookieStorage?.clearAll()
		}
	}
	
	private fun HttpRequestBuilder.addAuthHeader() {
		if (apiKey.isNotEmpty()) {
			header("X-Api-Key", apiKey)
		}
	}

	/**
	 * Fetches a fresh CSRF token from the server.
	 * CSRF tokens must be fetched before each state-changing request (POST, DELETE, PUT)
	 * when CSRF protection is enabled on the Jellyseerr server.
	 * 
	 * @param endpoint The API endpoint to fetch the token from (uses GET request)
	 * @return The CSRF token if found, null otherwise
	 */
	private suspend fun fetchCsrfToken(endpoint: String): String? {
		val url = URLBuilder("$baseUrl$endpoint").build()
		Timber.d("Jellyseerr: Fetching CSRF token via GET request to $endpoint")
		
		return try {
			val csrfResponse = httpClient.get(url) {
				addAuthHeader()
			}
			Timber.d("Jellyseerr: CSRF token fetch response - Status: ${csrfResponse.status.value}")
			
			// Extract CSRF token from stored cookies (after Ktor's HttpCookies plugin processes Set-Cookie headers)
			val storedCookies = cookieStorage?.get(url) ?: emptyList()
			Timber.d("Jellyseerr: Found ${storedCookies.size} stored cookies after GET request")
			
			var csrfToken: String? = null
			for (cookie in storedCookies) {
				Timber.d("Jellyseerr: Stored cookie: ${cookie.name} (domain: ${cookie.domain}, path: ${cookie.path})")
				if (cookie.name == "XSRF-TOKEN") {
					csrfToken = cookie.value
					Timber.d("Jellyseerr: XSRF-TOKEN cookie found with value: ${csrfToken.take(10)}...")
					break
				}
			}
			
			// Fallback: manually parse Set-Cookie headers if cookie wasn't stored properly
			if (csrfToken == null) {
				Timber.d("Jellyseerr: No XSRF-TOKEN cookie in storage, trying to parse from headers")
				val setCookieHeaders = csrfResponse.headers.getAll("Set-Cookie") ?: emptyList()
				Timber.d("Jellyseerr: Found ${setCookieHeaders.size} Set-Cookie headers")
				for (cookieHeader in setCookieHeaders) {
					Timber.d("Jellyseerr: Processing cookie header: ${cookieHeader.take(100)}...")
					val cookiePart = cookieHeader.split(";").firstOrNull()?.trim() ?: continue
					val parts = cookiePart.split("=", limit = 2)
					if (parts.size == 2) {
						val name = parts[0].trim()
						val value = parts[1].trim()
						if (name == "XSRF-TOKEN") {
							csrfToken = value
							Timber.d("Jellyseerr: XSRF-TOKEN parsed from header!")
							break
						}
					}
				}
			}
			
			if (csrfToken != null) {
				Timber.d("Jellyseerr: CSRF token ready: ${csrfToken.take(10)}...")
			} else {
				Timber.d("Jellyseerr: No XSRF-TOKEN cookie found (CSRF protection may be disabled)")
			}
			
			csrfToken
		} catch (e: Exception) {
			Timber.w("Jellyseerr: Failed to fetch CSRF token (non-critical): ${e.message}")
			// Continue anyway - server might not require CSRF
			null
		}
	}

	/**
	 * Adds CSRF token headers to a request if the token is available
	 */
	private fun HttpRequestBuilder.addCsrfHeaders(csrfToken: String?) {
		if (csrfToken != null) {
			header("X-CSRF-Token", csrfToken)
			header("X-XSRF-TOKEN", csrfToken)
		}
	}

	private val jsonConfig = Json {
		ignoreUnknownKeys = true
		prettyPrint = false
		encodeDefaults = false
		coerceInputValues = true
	}

	init {
		initializeCookieStorage(context)
	}

	private val httpClient = HttpClient(OkHttp) {
		install(ContentNegotiation) {
			json(jsonConfig)
		}
		
		install(HttpCookies) {
			storage = cookieStorage!!
		}

		engine {
			config {
				connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				followRedirects(true)
			}
		}
	}

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
			filter?.let { parameters.append("filter", it) }
			requestedBy?.let { parameters.append("requestedBy", it.toString()) }
		}.build()

		val response = httpClient.get(url) {
			addAuthHeader()
		}

		Timber.d("Jellyseerr: Got requests - Status: ${response.status}")
		response.body<JellyseerrListResponse<JellyseerrRequestDto>>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get requests")
	}

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
		
		// Fetch CSRF token before state-changing request
		val csrfToken = fetchCsrfToken("/api/v1/request")
		
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
			addCsrfHeaders(csrfToken)
			contentType(ContentType.Application.Json)
			setBody(requestBody)
		}

		Timber.d("Jellyseerr: Created request for $mediaType:$mediaId (4K=$is4k, profileId=$profileId) - Status: ${response.status}")
		
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: Request failed with status ${response.status}: $errorBody")
			throw Exception("Failed to create request: ${response.status} - $errorBody")
		}
		
		response.body<JellyseerrRequestDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to create request for $mediaType:$mediaId")
	}

	suspend fun deleteRequest(requestId: Int): Result<Unit> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/request/$requestId").build()
		
		// Fetch CSRF token before state-changing request
		val csrfToken = fetchCsrfToken("/api/v1/request/$requestId")
		
		val response = httpClient.delete(url) {
			addAuthHeader()
			addCsrfHeaders(csrfToken)
		}

		Timber.d("Jellyseerr: Deleted request $requestId - Status: ${response.status}")
		
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: Delete request failed with status ${response.status}: $errorBody")
			throw Exception("Failed to delete request: ${response.status} - $errorBody")
		}
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to delete request $requestId")
	}

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
		
		Timber.d("Jellyseerr: Got trending content - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get trending content")
	}

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
		
		Timber.d("Jellyseerr: Got trending movies - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get trending movies")
	}

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
		
		Timber.d("Jellyseerr: Got trending TV - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get trending TV shows")
	}

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
		val encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
		val page = ((offset / limit) + 1).toString()
		
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

	/**
	 * Get movie recommendations for a given movie ID
	 */
	suspend fun getRecommendationsMovies(tmdbId: Int, page: Int = 1): Result<JellyseerrDiscoverPageDto> = runCatching {
		val url = "$baseUrl/api/v1/movie/$tmdbId/recommendations"
		val response = httpClient.get(url) {
			addAuthHeader()
			url {
				parameters.append("page", page.toString())
			}
		}

		Timber.d("Jellyseerr: Got recommendations for movie $tmdbId - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get recommendations for movie $tmdbId")
	}

	/**
	 * Get TV show recommendations for a given TV show ID
	 */
	suspend fun getRecommendationsTv(tmdbId: Int, page: Int = 1): Result<JellyseerrDiscoverPageDto> = runCatching {
		val url = "$baseUrl/api/v1/tv/$tmdbId/recommendations"
		val response = httpClient.get(url) {
			addAuthHeader()
			url {
				parameters.append("page", page.toString())
			}
		}

		Timber.d("Jellyseerr: Got recommendations for TV show $tmdbId - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get recommendations for TV show $tmdbId")
	}

	/**
	 * Get genre slider for movies with backdrop images
	 */
	suspend fun getGenreSliderMovies(): Result<List<JellyseerrGenreDto>> = runCatching {
		val url = "$baseUrl/api/v1/discover/genreslider/movie"
		val response = httpClient.get(url) {
			addAuthHeader()
		}

		Timber.d("Jellyseerr: Got movie genres - Status: ${response.status}")
		response.body<List<JellyseerrGenreDto>>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get movie genres")
	}

	/**
	 * Get genre slider for TV shows with backdrop images
	 */
	suspend fun getGenreSliderTv(): Result<List<JellyseerrGenreDto>> = runCatching {
		val url = "$baseUrl/api/v1/discover/genreslider/tv"
		val response = httpClient.get(url) {
			addAuthHeader()
		}

		Timber.d("Jellyseerr: Got TV genres - Status: ${response.status}")
		response.body<List<JellyseerrGenreDto>>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get TV genres")
	}

	/**
	 * Discover movies with optional filters
	 * @param page Page number
	 * @param sortBy Sort method (e.g., "popularity.desc", "vote_average.desc")
	 * @param genre Genre ID to filter by
	 * @param studio Studio ID to filter by
	 * @param keywords Keyword ID to filter by
	 * @param language Language code (default: "en")
	 */
	suspend fun discoverMovies(
		page: Int = 1,
		sortBy: String = "popularity.desc",
		genre: Int? = null,
		studio: Int? = null,
		keywords: Int? = null,
		language: String = "en"
	): Result<JellyseerrDiscoverPageDto> = runCatching {
		val url = "$baseUrl/api/v1/discover/movies"
		val response = httpClient.get(url) {
			addAuthHeader()
			url {
				parameters.append("page", page.toString())
				parameters.append("sortBy", sortBy)
				parameters.append("language", language)
				genre?.let { parameters.append("genre", it.toString()) }
				studio?.let { parameters.append("studio", it.toString()) }
				keywords?.let { parameters.append("keywords", it.toString()) }
			}
		}

		Timber.d("Jellyseerr: Discovered movies (genre=$genre, studio=$studio, keywords=$keywords) - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to discover movies")
	}

	/**
	 * Discover TV shows with optional filters
	 * @param page Page number
	 * @param sortBy Sort method (e.g., "popularity.desc", "vote_average.desc")
	 * @param genre Genre ID to filter by
	 * @param network Network ID to filter by
	 * @param keywords Keyword ID to filter by
	 * @param language Language code (default: "en")
	 */
	suspend fun discoverTv(
		page: Int = 1,
		sortBy: String = "popularity.desc",
		genre: Int? = null,
		network: Int? = null,
		keywords: Int? = null,
		language: String = "en"
	): Result<JellyseerrDiscoverPageDto> = runCatching {
		val url = "$baseUrl/api/v1/discover/tv"
		val response = httpClient.get(url) {
			addAuthHeader()
			url {
				parameters.append("page", page.toString())
				parameters.append("sortBy", sortBy)
				parameters.append("language", language)
				genre?.let { parameters.append("genre", it.toString()) }
				network?.let { parameters.append("network", it.toString()) }
				keywords?.let { parameters.append("keywords", it.toString()) }
			}
		}

		Timber.d("Jellyseerr: Discovered TV shows (genre=$genre, network=$network, keywords=$keywords) - Status: ${response.status}")
		response.body<JellyseerrDiscoverPageDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to discover TV shows")
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
	 * Handles 308 redirects by upgrading HTTP to HTTPS
	 */
	suspend fun loginLocal(email: String, password: String): Result<JellyseerrUserDto> = runCatching {
		// Clear existing cookies before login to prevent stale auth data from causing issues
		Timber.d("Jellyseerr: Clearing cookies before local login attempt")
		clearCookies()
		
		var url = URLBuilder("$baseUrl/api/v1/auth/local").build()
		val loginBody = mapOf("email" to email, "password" to password)
		
		Timber.d("Jellyseerr: Attempting local login to URL: $url")
		Timber.d("Jellyseerr: Base URL: $baseUrl")
		
		// Fetch CSRF token before login
		val csrfToken = fetchCsrfToken("/api/v1/auth/local")
		
		// Add a small delay to ensure cookies are fully persisted (helps with storage race conditions)
		kotlinx.coroutines.delay(100)
		
		var response = httpClient.post(url) {
			contentType(ContentType.Application.Json)
			setBody(loginBody)
			addCsrfHeaders(csrfToken)
		}
		
		Timber.d("Jellyseerr: Local login response - Status: ${response.status.value} ${response.status.description}")
		Timber.d("Jellyseerr: Response headers: ${response.headers.entries().joinToString { "${it.key}: ${it.value}" }}")
		
		if (response.status.value == 308) {
			val location = response.headers["Location"]
			if (location != null && location.startsWith("https://") && baseUrl.startsWith("http://")) {
				Timber.w("Jellyseerr: Received 308 redirect from HTTP to HTTPS. Retrying with HTTPS URL: $location")
				url = URLBuilder(location).build()
				
				response = httpClient.post(url) {
					contentType(ContentType.Application.Json)
					setBody(loginBody)
					addCsrfHeaders(csrfToken)
				}
				
				Timber.d("Jellyseerr: HTTPS retry response - Status: ${response.status.value} ${response.status.description}")
			} else {
				Timber.e("Jellyseerr: Received 308 but no valid HTTPS redirect location found")
				throw Exception("Server requires HTTPS but redirect location is invalid. Location: $location")
			}
		}
		
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: Login failed with status ${response.status}: $errorBody")
			throw Exception("Login failed: ${response.status}")
		}
		
		val user = response.body<JellyseerrUserDto>()
		Timber.d("Jellyseerr: Successfully logged in as user: ${user.email}")
		user
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to login locally - ${error.message}")
	}

	/**
	 * Login with Jellyfin credentials
	 * First attempts without hostname (for already-configured servers)
	 * Falls back to including hostname on 401 (for initial server setup)
	 * Handles 308 redirects by upgrading HTTP to HTTPS
	 */
	suspend fun loginJellyfin(username: String, password: String, jellyfinUrl: String): Result<JellyseerrUserDto> = runCatching {
		// Clear existing cookies before login to prevent stale auth data from causing issues
		Timber.d("Jellyseerr: Clearing cookies before Jellyfin login attempt")
		clearCookies()
		
		var url = URLBuilder("$baseUrl/api/v1/auth/jellyfin").build()
		
		Timber.d("Jellyseerr: Attempting Jellyfin login to URL: $url")
		Timber.d("Jellyseerr: Base URL: $baseUrl")
		Timber.d("Jellyseerr: Jellyfin URL: $jellyfinUrl")
		Timber.d("Jellyseerr: Username: $username")
		
		// Fetch CSRF token before login
		val csrfToken = fetchCsrfToken("/api/v1/auth/jellyfin")
		
		// Add a small delay to ensure cookies are fully persisted (helps with storage race conditions)
		kotlinx.coroutines.delay(100)
		
		// First attempt: without hostname (standard for already-configured servers)
		Timber.d("Jellyseerr: Attempting login without hostname parameter")
		var response = httpClient.post(url) {
			contentType(ContentType.Application.Json)
			setBody(mapOf(
				"username" to username,
				"password" to password
			))
			addCsrfHeaders(csrfToken)
		}
		
		Timber.d("Jellyseerr: Jellyfin login response - Status: ${response.status.value} ${response.status.description}")
		Timber.d("Jellyseerr: Response headers: ${response.headers.entries().joinToString { "${it.key}: ${it.value}" }}")
		
		// Handle 308 redirect (HTTP -> HTTPS upgrade)
		if (response.status.value == 308) {
			val location = response.headers["Location"]
			if (location != null && location.startsWith("https://") && baseUrl.startsWith("http://")) {
				Timber.w("Jellyseerr: Received 308 redirect from HTTP to HTTPS. Retrying with HTTPS URL: $location")
				url = URLBuilder(location).build()
				
				response = httpClient.post(url) {
					contentType(ContentType.Application.Json)
					setBody(mapOf(
						"username" to username,
						"password" to password
					))
					addCsrfHeaders(csrfToken)
				}
				
				Timber.d("Jellyseerr: HTTPS retry response - Status: ${response.status.value} ${response.status.description}")
			} else {
				Timber.e("Jellyseerr: Received 308 but no valid HTTPS redirect location found")
				throw Exception("Server requires HTTPS but redirect location is invalid. Location: $location")
			}
		}
		
		// Success - return user
		if (response.status.value in 200..299) {
			val user = response.body<JellyseerrUserDto>()
			Timber.d("Jellyseerr: Successfully logged in as user: ${user.email}")
			return@runCatching user
		}
		
		// 401 - Server not configured yet, retry with hostname
		if (response.status.value == 401) {
			Timber.d("Jellyseerr: Received 401, retrying with hostname parameter")
			response = httpClient.post(url) {
				contentType(ContentType.Application.Json)
				setBody(mapOf(
					"username" to username,
					"password" to password,
					"hostname" to jellyfinUrl
				))
				addCsrfHeaders(csrfToken)
			}
			
			Timber.d("Jellyseerr: Second attempt response - Status: ${response.status.value} ${response.status.description}")
			
			if (response.status.value in 200..299) {
				val user = response.body<JellyseerrUserDto>()
				Timber.d("Jellyseerr: Successfully logged in as user: ${user.email}")
				return@runCatching user
			}
			
			// Handle errors from second attempt
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: Second login attempt failed: $errorBody")
			throw Exception("Jellyfin login failed: ${response.status} - $errorBody")
		}
		
		// 500 - Server error, likely configuration issue
		if (response.status.value == 500) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: Server error (500): $errorBody")
			
			val errorMessage = if (errorBody.contains("Something went wrong", ignoreCase = true)) {
				"Server configuration error. Verify that:\n" +
				"• Jellyseerr server URL is correct: $baseUrl\n" +
				"• Jellyfin server URL in Jellyseerr settings matches: $jellyfinUrl\n" +
				"• Username and password are correct"
			} else {
				"Authentication failed. Verify your username and password are correct, and that the Jellyfin server URL in Jellyseerr settings matches: $jellyfinUrl"
			}
			
			throw Exception(errorMessage)
		}
		
		// Other errors
		val errorBody = response.body<String>()
		Timber.e("Jellyseerr: Unexpected status ${response.status}: $errorBody")
		throw Exception("Jellyfin login failed: ${response.status} - $errorBody")
	}.onFailure { error ->
		// Error already logged above, just re-throw without redundant logging
		if (error.message?.contains("configuration error") == true || error.message?.contains("Authentication failed") == true) {
			// These are user-facing errors, don't log stack trace
			Timber.w("Jellyseerr: Jellyfin login failed - ${error.message}")
		} else {
			// Unexpected errors should include stack trace
			Timber.e(error, "Jellyseerr: Failed to login with Jellyfin - ${error.message}")
		}
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
		
		// Fetch CSRF token before state-changing request
		val csrfToken = fetchCsrfToken("/api/v1/settings/main/regenerate")
		
		val response = httpClient.post(url) {
			addAuthHeader()
			addCsrfHeaders(csrfToken)
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
	 * Get list of Radarr servers available to the current user
	 * Uses /api/v1/service/radarr which is available to all authenticated users
	 */
	suspend fun getRadarrServers(): Result<List<JellyseerrServiceServerDto>> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/service/radarr").build()
		val response = httpClient.get(url) {
			addAuthHeader()
		}
		
		Timber.d("Jellyseerr: Got Radarr servers - Status: ${response.status}")
		
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: getRadarrServers failed with status ${response.status}: $errorBody")
			throw Exception("Failed to get Radarr servers: ${response.status}")
		}
		
		response.body<List<JellyseerrServiceServerDto>>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get Radarr servers")
	}

	/**
	 * Get detailed info for a specific Radarr server
	 * Uses /api/v1/service/radarr/:id which is available to all authenticated users
	 */
	suspend fun getRadarrServerDetails(serverId: Int): Result<JellyseerrServiceServerDetailsDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/service/radarr/$serverId").build()
		val response = httpClient.get(url) {
			addAuthHeader()
		}
		
		Timber.d("Jellyseerr: Got Radarr server details for $serverId - Status: ${response.status}")
		
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: getRadarrServerDetails failed with status ${response.status}: $errorBody")
			throw Exception("Failed to get Radarr server details: ${response.status}")
		}
		
		response.body<JellyseerrServiceServerDetailsDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get Radarr server details")
	}

	/**
	 * Get list of Sonarr servers available to the current user
	 * Uses /api/v1/service/sonarr which is available to all authenticated users
	 */
	suspend fun getSonarrServers(): Result<List<JellyseerrServiceServerDto>> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/service/sonarr").build()
		val response = httpClient.get(url) {
			addAuthHeader()
		}
		
		Timber.d("Jellyseerr: Got Sonarr servers - Status: ${response.status}")
		
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: getSonarrServers failed with status ${response.status}: $errorBody")
			throw Exception("Failed to get Sonarr servers: ${response.status}")
		}
		
		response.body<List<JellyseerrServiceServerDto>>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get Sonarr servers")
	}

	/**
	 * Get detailed info for a specific Sonarr server
	 * Uses /api/v1/service/sonarr/:id which is available to all authenticated users
	 */
	suspend fun getSonarrServerDetails(serverId: Int): Result<JellyseerrServiceServerDetailsDto> = runCatching {
		val url = URLBuilder("$baseUrl/api/v1/service/sonarr/$serverId").build()
		val response = httpClient.get(url) {
			addAuthHeader()
		}
		
		Timber.d("Jellyseerr: Got Sonarr server details for $serverId - Status: ${response.status}")
		
		if (response.status.value !in 200..299) {
			val errorBody = response.body<String>()
			Timber.e("Jellyseerr: getSonarrServerDetails failed with status ${response.status}: $errorBody")
			throw Exception("Failed to get Sonarr server details: ${response.status}")
		}
		
		response.body<JellyseerrServiceServerDetailsDto>()
	}.onFailure { error ->
		Timber.e(error, "Jellyseerr: Failed to get Sonarr server details")
	}

	/**
	 * Get all Radarr server configurations (ADMIN ONLY)
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
