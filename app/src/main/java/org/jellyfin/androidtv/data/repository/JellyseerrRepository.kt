package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrCreateRequestDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverPageDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrHttpClient
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrListResponse
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrMovieDetailsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrPersonCombinedCreditsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrPersonDetailsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrRequestDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrTvDetailsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrUserDto
import org.jellyfin.androidtv.data.service.jellyseerr.Seasons
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import timber.log.Timber

/**
 * Repository for Jellyseerr operations
 * Manages requests, discover content, and connection state
 */
interface JellyseerrRepository {
	val isAvailable: StateFlow<Boolean>

	/**
	 * Ensure the repository is initialized from saved preferences
	 */
	suspend fun ensureInitialized()

	/**
	 * Initialize the repository with server configuration
	 */
	suspend fun initialize(serverUrl: String, apiKey: String): Result<Unit>

	/**
	 * Test the connection to Jellyseerr
	 */
	suspend fun testConnection(): Result<Boolean>

	/**
	 * Get movie details including cast
	 */
	suspend fun getMovieDetails(tmdbId: Int): Result<JellyseerrMovieDetailsDto>

	/**
	 * Get TV show details including cast
	 */
	suspend fun getTvDetails(tmdbId: Int): Result<JellyseerrTvDetailsDto>

	/**
	 * Authenticate with Jellyfin and get API key
	 */
	suspend fun loginWithJellyfin(username: String, password: String, jellyfinUrl: String, jellyseerrUrl: String): Result<JellyseerrUserDto>

	/**
	 * Check if the current session is still valid (cookie-based auth)
	 * This verifies that the stored session cookie from a previous login is still active
	 */
	suspend fun isSessionValid(): Result<Boolean>

	/**
	 * Get current authenticated user
	 */
	suspend fun getCurrentUser(): Result<JellyseerrUserDto>

	/**
	 * Get all requests visible to the current user
	 */
	suspend fun getRequests(
		filter: String? = null,
		requestedBy: Int? = null,
		limit: Int = 50,
		offset: Int = 0,
	): Result<JellyseerrListResponse<JellyseerrRequestDto>>

	/**
	 * Create a new request for a movie or TV show
	 */
	suspend fun createRequest(
		mediaId: Int,
		mediaType: String,
		seasons: Seasons? = null,
		is4k: Boolean = false,
	): Result<JellyseerrRequestDto>

	/**
	 * Delete an existing request
	 */
	suspend fun deleteRequest(requestId: Int): Result<Unit>

	/**
	 * Get trending movies
	 */
	suspend fun getTrendingMovies(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto>

	/**
	 * Get trending TV shows
	 */
	suspend fun getTrendingTv(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto>

	/**
	 * Get trending content (movies and TV combined)
	 */
	suspend fun getTrending(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto>

	/**
	 * Get top-rated movies
	 */
	suspend fun getTopMovies(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto>

	/**
	 * Get top-rated TV shows
	 */
	suspend fun getTopTv(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto>

	/**
	 * Get upcoming movies
	 */
	suspend fun getUpcomingMovies(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto>

	/**
	 * Get upcoming TV shows
	 */
	suspend fun getUpcomingTv(
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto>

	/**
	 * Search for movies or TV shows
	 */
	suspend fun search(
		query: String,
		mediaType: String? = null,
		limit: Int = 20,
		offset: Int = 0,
	): Result<JellyseerrDiscoverPageDto>

	/**
	 * Get similar movies for a given movie ID
	 */
	suspend fun getSimilarMovies(tmdbId: Int, page: Int = 1): Result<JellyseerrDiscoverPageDto>

	/**
	 * Get similar TV shows for a given TV show ID
	 */
	suspend fun getSimilarTv(tmdbId: Int, page: Int = 1): Result<JellyseerrDiscoverPageDto>

	/**
	 * Get person details by ID
	 */
	suspend fun getPersonDetails(personId: Int): Result<JellyseerrPersonDetailsDto>

	/**
	 * Get combined credits (movies and TV) for a person
	 */
	suspend fun getPersonCombinedCredits(personId: Int): Result<JellyseerrPersonCombinedCreditsDto>

	/**
	 * Cleanup resources
	 */
	fun close()
}

class JellyseerrRepositoryImpl(
	private val context: android.content.Context,
	private val preferences: JellyseerrPreferences,
) : JellyseerrRepository {
	private var httpClient: JellyseerrHttpClient? = null
	private val _isAvailable = MutableStateFlow(false)
	override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()
	private var initialized = false

	/**
	 * Auto-initialize from saved preferences if available
	 */
	override suspend fun ensureInitialized() {
		// Reset initialization if client is no longer available
		if (initialized && httpClient == null) {
			initialized = false
		}

		if (initialized) return
		if (httpClient != null) {
			initialized = true
			return
		}

		withContext(Dispatchers.IO) {
			try {
				val serverUrl = preferences[JellyseerrPreferences.serverUrl]
				val enabled = preferences[JellyseerrPreferences.enabled]

			// Initialize with cookie-based Jellyfin auth (no API key needed)
			if (enabled && !serverUrl.isNullOrEmpty()) {
				Timber.d("Jellyseerr: Auto-initializing from saved preferences")
				httpClient = JellyseerrHttpClient(context, serverUrl, "")
				
				val connectionTest = httpClient?.testConnection()?.getOrNull() == true
					_isAvailable.emit(connectionTest)
					initialized = true
					Timber.d("Jellyseerr: Auto-initialized - Available: $connectionTest")
				} else {
					Timber.w("Jellyseerr: Jellyseerr is disabled or not configured")
					_isAvailable.emit(false)
					initialized = true
				}
			} catch (error: Exception) {
				Timber.w(error, "Jellyseerr: Failed to auto-initialize from preferences")
				_isAvailable.emit(false)
				initialized = true
			}
		}
	}

	override suspend fun initialize(serverUrl: String, apiKey: String): Result<Unit> =
		withContext(Dispatchers.IO) {
			try {
				// Clean up old client if it exists
				httpClient?.close()
				initialized = false

				// Create new client
				httpClient = JellyseerrHttpClient(context, serverUrl, apiKey)

				// Test connection
				val connectionTest = httpClient?.testConnection()?.getOrNull() == true
				_isAvailable.emit(connectionTest)

				// Save preferences
				preferences[JellyseerrPreferences.serverUrl] = serverUrl
				preferences[JellyseerrPreferences.enabled] = true
				initialized = true

				Timber.d("Jellyseerr: Initialized - Available: $connectionTest")
				Result.success(Unit)
			} catch (error: Exception) {
				Timber.e(error, "Jellyseerr: Failed to initialize")
				_isAvailable.emit(false)
				Result.failure(error)
			}
		}

	override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			Timber.w("Jellyseerr: No client initialized and no saved preferences")
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		val result = client.testConnection()
		if (result.isSuccess) {
			_isAvailable.emit(true)
		}
		result
	}

	override suspend fun getMovieDetails(tmdbId: Int): Result<JellyseerrMovieDetailsDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getMovieDetails(tmdbId)
	}

	override suspend fun getTvDetails(tmdbId: Int): Result<JellyseerrTvDetailsDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getTvDetails(tmdbId)
	}

	override suspend fun loginWithJellyfin(username: String, password: String, jellyfinUrl: String, jellyseerrUrl: String): Result<JellyseerrUserDto> = withContext(Dispatchers.IO) {
		// Create temporary client without API key for authentication
		val tempClient = JellyseerrHttpClient(context, jellyseerrUrl, "")
		tempClient.loginJellyfin(username, password, jellyfinUrl)
	}

	override suspend fun isSessionValid(): Result<Boolean> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.success(false)
		}

		// Try to get current user - if successful, session is valid
		val result = client.getCurrentUser()
		Result.success(result.isSuccess)
	}

	override suspend fun getCurrentUser(): Result<JellyseerrUserDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getCurrentUser()
	}

	override suspend fun getRequests(
		filter: String?,
		requestedBy: Int?,
		limit: Int,
		offset: Int,
	): Result<JellyseerrListResponse<JellyseerrRequestDto>> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getRequests(filter = filter, requestedBy = requestedBy, limit = limit, offset = offset)
	}

	override suspend fun createRequest(
		mediaId: Int,
		mediaType: String,
		seasons: Seasons?,
		is4k: Boolean,
	): Result<JellyseerrRequestDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.createRequest(mediaId, mediaType, seasons, is4k)
	}

	override suspend fun deleteRequest(requestId: Int): Result<Unit> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.deleteRequest(requestId)
	}

	override suspend fun getTrendingMovies(
		limit: Int,
		offset: Int,
	): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getTrendingMovies(limit, offset)
	}

	override suspend fun getTrendingTv(
		limit: Int,
		offset: Int,
	): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getTrendingTv(limit, offset)
	}

	override suspend fun getTrending(
		limit: Int,
		offset: Int,
	): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getTrending(limit, offset)
	}

	override suspend fun getTopMovies(
		limit: Int,
		offset: Int,
	): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getTopMovies(limit, offset)
	}

	override suspend fun getTopTv(
		limit: Int,
		offset: Int,
	): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getTopTv(limit, offset)
	}

	override suspend fun getUpcomingMovies(
		limit: Int,
		offset: Int,
	): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getUpcomingMovies(limit, offset)
	}

	override suspend fun getUpcomingTv(
		limit: Int,
		offset: Int,
	): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getUpcomingTv(limit, offset)
	}

	override suspend fun search(
		query: String,
		mediaType: String?,
		limit: Int,
		offset: Int,
	): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.search(query, mediaType, limit, offset)
	}

	override suspend fun getSimilarMovies(tmdbId: Int, page: Int): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getSimilarMovies(tmdbId, page)
	}

	override suspend fun getSimilarTv(tmdbId: Int, page: Int): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getSimilarTv(tmdbId, page)
	}

	override suspend fun getPersonDetails(personId: Int): Result<JellyseerrPersonDetailsDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getPersonDetails(personId)
	}

	override suspend fun getPersonCombinedCredits(personId: Int): Result<JellyseerrPersonCombinedCreditsDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getPersonCombinedCredits(personId)
	}

	override fun close() {
		httpClient?.close()
		httpClient = null
	}
}
