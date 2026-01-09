package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrBlacklistPageDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrCreateRequestDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverPageDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrGenreDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrHttpClient
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrListResponse
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrMovieDetailsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrPersonCombinedCreditsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrPersonDetailsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrRadarrSettingsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrRequestDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrServiceServerDetailsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrServiceServerDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrSonarrSettingsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrTvDetailsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrUserDto
import org.jellyfin.androidtv.data.service.jellyseerr.Seasons
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import timber.log.Timber
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

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
	 * Authenticate with Jellyfin SSO (cookie-based, 30-day expiration)
	 */
	suspend fun loginWithJellyfin(username: String, password: String, jellyfinUrl: String, jellyseerrUrl: String): Result<JellyseerrUserDto>

	/**
	 * Authenticate with local Jellyseerr credentials (returns API key for permanent auth)
	 */
	suspend fun loginLocal(email: String, password: String, jellyseerrUrl: String): Result<JellyseerrUserDto>

	/**
	 * Regenerate API key for current user (requires active session)
	 * Use after Jellyfin login to get permanent API key instead of 30-day cookies
	 */
	suspend fun regenerateApiKey(): Result<String>

	/**
	 * Check if the current session is still valid (cookie-based auth)
	 * This verifies that the stored session cookie from a previous login is still active
	 */
	suspend fun isSessionValid(): Result<Boolean>

	/**
	 * Check session validity with caching to reduce repeated network calls.
	 */
	suspend fun isSessionValidCached(): Boolean

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
		profileId: Int? = null,
		rootFolderId: Int? = null,
		serverId: Int? = null,
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
	 * Get movie recommendations for a given movie ID
	 */
	suspend fun getRecommendationsMovies(tmdbId: Int, page: Int = 1): Result<JellyseerrDiscoverPageDto>

	/**
	 * Get TV show recommendations for a given TV show ID
	 */
	suspend fun getRecommendationsTv(tmdbId: Int, page: Int = 1): Result<JellyseerrDiscoverPageDto>

	/**
	 * Get person details by ID
	 */
	suspend fun getPersonDetails(personId: Int): Result<JellyseerrPersonDetailsDto>

	/**
	 * Get combined credits (movies and TV) for a person
	 */
	suspend fun getPersonCombinedCredits(personId: Int): Result<JellyseerrPersonCombinedCreditsDto>

	/**
	 * Get the blacklist
	 */
	suspend fun getBlacklist(): Result<JellyseerrBlacklistPageDto>

	/**
	 * Get genre slider for movies
	 */
	suspend fun getGenreSliderMovies(): Result<List<JellyseerrGenreDto>>

	/**
	 * Get genre slider for TV shows
	 */
	suspend fun getGenreSliderTv(): Result<List<JellyseerrGenreDto>>

	/**
	 * Discover movies with optional filters
	 */
	suspend fun discoverMovies(
		page: Int = 1,
		sortBy: String = "popularity.desc",
		genre: Int? = null,
		studio: Int? = null,
		keywords: Int? = null,
		language: String = "en"
	): Result<JellyseerrDiscoverPageDto>

	/**
	 * Discover TV shows with optional filters
	 */
	suspend fun discoverTv(
		page: Int = 1,
		sortBy: String = "popularity.desc",
		genre: Int? = null,
		network: Int? = null,
		keywords: Int? = null,
		language: String = "en"
	): Result<JellyseerrDiscoverPageDto>

	/**
	 * Get list of Radarr servers available to the current user
	 * Uses /api/v1/service/radarr - available to all authenticated users
	 */
	suspend fun getRadarrServers(): Result<List<JellyseerrServiceServerDto>>

	/**
	 * Get detailed info for a specific Radarr server
	 * Uses /api/v1/service/radarr/:id - available to all authenticated users
	 */
	suspend fun getRadarrServerDetails(serverId: Int): Result<JellyseerrServiceServerDetailsDto>

	/**
	 * Get list of Sonarr servers available to the current user
	 * Uses /api/v1/service/sonarr - available to all authenticated users
	 */
	suspend fun getSonarrServers(): Result<List<JellyseerrServiceServerDto>>

	/**
	 * Get detailed info for a specific Sonarr server
	 * Uses /api/v1/service/sonarr/:id - available to all authenticated users
	 */
	suspend fun getSonarrServerDetails(serverId: Int): Result<JellyseerrServiceServerDetailsDto>

	/**
	 * Get all Radarr server configurations (ADMIN ONLY)
	 */
	suspend fun getRadarrSettings(): Result<List<JellyseerrRadarrSettingsDto>>

	/**
	 * Get all Sonarr server configurations (ADMIN ONLY)
	 */
	suspend fun getSonarrSettings(): Result<List<JellyseerrSonarrSettingsDto>>

	/**
	 * Cleanup resources
	 */
	fun close()
}

class JellyseerrRepositoryImpl(
	private val context: android.content.Context,
	private val preferences: JellyseerrPreferences, // Global preferences (server URL, UI settings)
	private val userRepository: UserRepository,
) : JellyseerrRepository, KoinComponent {
	private var httpClient: JellyseerrHttpClient? = null
	private val _isAvailable = MutableStateFlow(false)
	override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()
	private var initialized = false
	private var lastUserId: String? = null // Track which user we're initialized for
	
	// Session validity cache to prevent excessive login attempts
	private var lastSessionCheckTime: Long = 0
	private var lastSessionValid: Boolean = false
	private companion object {
		// Cache session validity for 5 minutes to prevent excessive auth checks
		private const val SESSION_CACHE_DURATION_MS = 5 * 60 * 1000L
	}

	/**
	 * Get user-specific preferences for the current user
	 * This includes auth data, API keys, passwords, etc.
	 */
	private suspend fun getUserPreferences(): JellyseerrPreferences? {
		val user = userRepository.currentUser.first { it != null }
		return user?.id?.let { userId ->
			get(named("user")) { parametersOf(userId.toString()) }
		}
	}

	/**
	 * Auto-initialize from saved preferences if available
	 * Waits for user to be set before initializing to ensure correct cookie storage is used
	 * Will attempt to re-authenticate if session is invalid and credentials are saved
	 */
	override suspend fun ensureInitialized() {
		// Get current user
		val currentUser = userRepository.currentUser.value
		val currentUserId = currentUser?.id?.toString()
		
		// Reset initialization if user has changed
		if (initialized && currentUserId != null && currentUserId != lastUserId) {
			initialized = false
			httpClient?.close()
			httpClient = null
			_isAvailable.emit(false)
			// Invalidate session cache when user changes
			invalidateSessionCache()
		}
		
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
				// Get global settings (server URL, enabled state)
				val serverUrl = preferences[JellyseerrPreferences.serverUrl]
				val enabled = preferences[JellyseerrPreferences.enabled]
				
				// Wait for user to be available (needed for user-specific preferences)
				val user = withTimeoutOrNull(5000L) {
					userRepository.currentUser.first { it != null }
				}
				
			if (user == null) {
				_isAvailable.emit(false)
				initialized = true
				return@withContext
			}				// Switch cookie storage to current user
				JellyseerrHttpClient.switchCookieStorage(user.id.toString())
				
				// Remember which user we're initialized for
				lastUserId = user.id.toString()
				
				// Get user-specific auth data
				val userPrefs = getUserPreferences()
				val storedApiKey = userPrefs?.get(JellyseerrPreferences.apiKey) ?: ""
				val authMethod = userPrefs?.get(JellyseerrPreferences.authMethod) ?: ""

				// Initialize with stored API key or cookie-based auth
				if (enabled && !serverUrl.isNullOrEmpty()) {
					httpClient = JellyseerrHttpClient(context, serverUrl, storedApiKey)
					
					// Check if we have a recent valid session in cache first
					val now = System.currentTimeMillis()
					val cacheAge = now - lastSessionCheckTime
					val useCache = cacheAge < SESSION_CACHE_DURATION_MS && lastSessionValid
					
					val sessionValid = if (useCache) {
						Timber.d("Jellyseerr: Using cached session validity during init (age: ${cacheAge / 1000}s)")
						true
					} else {
						// Verify the session is actually valid by calling getCurrentUser
						val result = httpClient?.getCurrentUser()?.isSuccess == true
						// Update cache with result
						lastSessionCheckTime = now
						lastSessionValid = result
						result
					}
					
					if (!sessionValid) {
						// Auth failed - try auto-relogin based on auth method
						when (authMethod) {
							"jellyfin" -> {
								// Cookie-based Jellyfin auth failed, try to re-authenticate with saved password
								if (storedApiKey.isEmpty()) {
									val savedPassword = userPrefs?.get(JellyseerrPreferences.password) ?: ""
									if (savedPassword.isNotEmpty()) {
										Timber.i("Jellyseerr: Jellyfin session expired, attempting auto-re-login with saved credentials")
										val jellyfinUser = userRepository.currentUser.value
										if (jellyfinUser != null) {
											val username = jellyfinUser.name ?: ""
											// Get Jellyfin server URL from the API client
											val jellyfinUrl = org.koin.core.context.GlobalContext.get().get<org.jellyfin.sdk.api.client.ApiClient>().baseUrl ?: ""
											if (username.isNotEmpty() && jellyfinUrl.isNotEmpty()) {
												val reloginResult = httpClient?.loginJellyfin(username, savedPassword, jellyfinUrl)
												if (reloginResult?.isSuccess == true) {
													Timber.i("Jellyseerr: Auto-re-login successful")
													// Update cache on successful re-login
													lastSessionCheckTime = System.currentTimeMillis()
													lastSessionValid = true
													_isAvailable.emit(true)
													initialized = true
													return@withContext
												} else {
													Timber.w("Jellyseerr: Auto-re-login failed: ${reloginResult?.exceptionOrNull()?.message}")
												}
											}
										}
									} else {
										Timber.i("Jellyseerr: Session expired but no password saved (passwordless Jellyfin user). Please reconnect manually.")
									}
								}
							}
							"jellyfin-apikey" -> {
								// API key-based Jellyfin auth - check if key is still valid
								Timber.w("Jellyseerr: Jellyfin API key auth failed, may need to regenerate")
								// Don't auto-relogin with password for API key auth - the key should be permanent
							}
							"local" -> {
								// Local account auth failed, try to re-authenticate with saved credentials
								val savedEmail = userPrefs?.get(JellyseerrPreferences.localEmail) ?: ""
								val savedPassword = userPrefs?.get(JellyseerrPreferences.localPassword) ?: ""
								if (savedEmail.isNotEmpty() && savedPassword.isNotEmpty()) {
									Timber.i("Jellyseerr: Local session expired or API key invalid, attempting auto-re-login")
									val reloginResult = httpClient?.loginLocal(savedEmail, savedPassword)
									if (reloginResult?.isSuccess == true) {
										val newApiKey = reloginResult.getOrNull()?.apiKey ?: ""
										if (newApiKey.isNotEmpty() && userPrefs != null) {
											Timber.i("Jellyseerr: Local auto-re-login successful, updating API key")
											userPrefs[JellyseerrPreferences.apiKey] = newApiKey
											httpClient?.close()
											httpClient = JellyseerrHttpClient(context, serverUrl, newApiKey)
											// Update cache on successful re-login
											lastSessionCheckTime = System.currentTimeMillis()
											lastSessionValid = true
											_isAvailable.emit(true)
											initialized = true
											return@withContext
										}
									} else {
										Timber.w("Jellyseerr: Local auto-re-login failed")
									}
								} else {
									Timber.w("Jellyseerr: Local session expired but no saved credentials for auto-re-login")
								}
							}
							else -> {
								Timber.w("Jellyseerr: Unknown auth method '$authMethod', cannot auto-relogin")
							}
						}
					}
					
					_isAvailable.emit(sessionValid)
					initialized = true
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
		// Get current Jellyfin user to associate this Jellyseerr login
		val jellyfinUser = userRepository.currentUser.value
		if (jellyfinUser == null) {
			Timber.w("Jellyseerr: No Jellyfin user logged in")
			return@withContext Result.failure(IllegalStateException("No Jellyfin user logged in"))
		}
		
		// Switch cookie storage to current user BEFORE login so cookies get saved to the right user
		JellyseerrHttpClient.switchCookieStorage(jellyfinUser.id.toString())
		
		// Create temporary client without API key for authentication
		val tempClient = JellyseerrHttpClient(context, jellyseerrUrl, "")
		val result = tempClient.loginJellyfin(username, password, jellyfinUrl)
		
		result.onSuccess { user ->
			// Get user-specific preferences for auth data
			val userPrefs = getUserPreferences() ?: return@onSuccess
			
			// Save password for auto-re-login when cookies expire (only if not empty)
			if (password.isNotEmpty()) {
				userPrefs[JellyseerrPreferences.password] = password
			} else {
				// User has passwordless login - clear any existing password
				userPrefs[JellyseerrPreferences.password] = ""
				Timber.i("Jellyseerr: User logged in without password, auto-relogin will not be available")
			}
			
			// Check if auto API key generation is enabled
			val autoGenerateApiKey = preferences[JellyseerrPreferences.autoGenerateApiKey]
			
			if (autoGenerateApiKey) {
				Timber.d("Jellyseerr: Auto API key generation enabled, regenerating API key...")
				
				// Initialize with empty API key first (using cookies for auth)
				initialize(jellyseerrUrl, "")
				
				// Try to regenerate API key to get permanent auth
				val apiKeyResult = regenerateApiKey()
				apiKeyResult.onSuccess { apiKey ->
					// Save the API key to user preferences
					userPrefs[JellyseerrPreferences.apiKey] = apiKey
					userPrefs[JellyseerrPreferences.authMethod] = "jellyfin-apikey"
					// Clear password since we have API key now
					userPrefs[JellyseerrPreferences.password] = ""
				}.onFailure { error ->
					Timber.w("Jellyseerr: Failed to auto-generate API key (requires admin permissions), using cookie auth")
					// Keep using cookie-based auth with saved password
					userPrefs[JellyseerrPreferences.authMethod] = "jellyfin"
				}
			} else {
				// Initialize client with empty API key (will use cookies)
				initialize(jellyseerrUrl, "")
				userPrefs[JellyseerrPreferences.authMethod] = "jellyfin"
			}
		}
		
		result
	}

	override suspend fun loginLocal(email: String, password: String, jellyseerrUrl: String): Result<JellyseerrUserDto> = withContext(Dispatchers.IO) {
		// Get current Jellyfin user to associate this Jellyseerr login
		val jellyfinUser = userRepository.currentUser.value
		if (jellyfinUser == null) {
			Timber.w("Jellyseerr Repository: No Jellyfin user logged in")
			return@withContext Result.failure(IllegalStateException("No Jellyfin user logged in"))
		}
		
		// Switch cookie storage to current user (for consistency, even though local login uses API keys)
		JellyseerrHttpClient.switchCookieStorage(jellyfinUser.id.toString())
		
		// Create temporary client without API key for authentication
		val tempClient = JellyseerrHttpClient(context, jellyseerrUrl, "")
		
		val result = tempClient.loginLocal(email, password)
		
		result.onSuccess { user ->
			val apiKey = user.apiKey ?: ""
			
			// Initialize client with the API key from response
			httpClient?.close()
			httpClient = JellyseerrHttpClient(context, jellyseerrUrl, apiKey)
			_isAvailable.emit(true)
			initialized = true
			
			// Save to global preferences
			preferences[JellyseerrPreferences.serverUrl] = jellyseerrUrl
			preferences[JellyseerrPreferences.enabled] = true
			
			// Save credentials and API key to user-specific preferences
			val userPrefs = getUserPreferences()
			if (userPrefs != null) {
				userPrefs[JellyseerrPreferences.authMethod] = "local"
				userPrefs[JellyseerrPreferences.localEmail] = email
				userPrefs[JellyseerrPreferences.localPassword] = password
				userPrefs[JellyseerrPreferences.apiKey] = apiKey
			}
		}.onFailure { error ->
			Timber.e(error, "Jellyseerr: Failed to login with local account")
		}
		
		result
	}

	override suspend fun regenerateApiKey(): Result<String> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		val result = client.regenerateApiKey()
		
		result.onSuccess { apiKey ->
			val serverUrl = preferences[JellyseerrPreferences.serverUrl]
			if (!serverUrl.isNullOrEmpty()) {
				initialize(serverUrl, apiKey)
				val userPrefs = getUserPreferences()
				if (userPrefs != null) {
					userPrefs[JellyseerrPreferences.apiKey] = apiKey
					userPrefs[JellyseerrPreferences.authMethod] = "jellyfin-apikey"
				}
			}
		}.onFailure { error ->
			Timber.e(error, "Jellyseerr: Failed to regenerate API key")
		}
		
		result
	}

	override suspend fun isSessionValid(): Result<Boolean> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.success(false)
		}

		// Try to get current user - if successful, session is valid
		val result = client.getCurrentUser()
		val isValid = result.isSuccess
		
		// Update cache
		lastSessionCheckTime = System.currentTimeMillis()
		lastSessionValid = isValid
		
		Result.success(isValid)
	}
	
	/**
	 * Check if session is valid using cached result when available.
	 * This prevents excessive authentication checks that can trigger rate limiting.
	 * Cache is valid for SESSION_CACHE_DURATION_MS (5 minutes).
	 */
	override suspend fun isSessionValidCached(): Boolean {
		val now = System.currentTimeMillis()
		val cacheAge = now - lastSessionCheckTime
		
		if (cacheAge < SESSION_CACHE_DURATION_MS && lastSessionValid) {
			Timber.d("Jellyseerr: Using cached session validity (age: ${cacheAge / 1000}s, valid: true)")
			return true
		}
		
		Timber.d("Jellyseerr: Session cache expired or invalid, checking fresh (age: ${cacheAge / 1000}s)")
		return isSessionValid().getOrDefault(false)
	}
	
	/**
	 * Invalidate the session cache, forcing the next check to verify with the server.
	 * Call this when logout occurs or session is known to be invalid.
	 */
	fun invalidateSessionCache() {
		Timber.d("Jellyseerr: Session cache invalidated")
		lastSessionCheckTime = 0
		lastSessionValid = false
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
		profileId: Int?,
		rootFolderId: Int?,
		serverId: Int?,
	): Result<JellyseerrRequestDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.createRequest(mediaId, mediaType, seasons, is4k, profileId, rootFolderId, serverId)
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

	override suspend fun getRecommendationsMovies(tmdbId: Int, page: Int): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getRecommendationsMovies(tmdbId, page)
	}

	override suspend fun getRecommendationsTv(tmdbId: Int, page: Int): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getRecommendationsTv(tmdbId, page)
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

	override suspend fun getBlacklist(): Result<JellyseerrBlacklistPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getBlacklist()
	}

	override suspend fun getGenreSliderMovies(): Result<List<JellyseerrGenreDto>> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getGenreSliderMovies()
	}

	override suspend fun getGenreSliderTv(): Result<List<JellyseerrGenreDto>> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getGenreSliderTv()
	}

	override suspend fun discoverMovies(
		page: Int,
		sortBy: String,
		genre: Int?,
		studio: Int?,
		keywords: Int?,
		language: String
	): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.discoverMovies(page, sortBy, genre, studio, keywords, language)
	}

	override suspend fun discoverTv(
		page: Int,
		sortBy: String,
		genre: Int?,
		network: Int?,
		keywords: Int?,
		language: String
	): Result<JellyseerrDiscoverPageDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.discoverTv(page, sortBy, genre, network, keywords, language)
	}

	override suspend fun getRadarrServers(): Result<List<JellyseerrServiceServerDto>> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getRadarrServers()
	}

	override suspend fun getRadarrServerDetails(serverId: Int): Result<JellyseerrServiceServerDetailsDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getRadarrServerDetails(serverId)
	}

	override suspend fun getSonarrServers(): Result<List<JellyseerrServiceServerDto>> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getSonarrServers()
	}

	override suspend fun getSonarrServerDetails(serverId: Int): Result<JellyseerrServiceServerDetailsDto> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getSonarrServerDetails(serverId)
	}

	override suspend fun getRadarrSettings(): Result<List<JellyseerrRadarrSettingsDto>> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getRadarrSettings()
	}

	override suspend fun getSonarrSettings(): Result<List<JellyseerrSonarrSettingsDto>> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: run {
			return@withContext Result.failure(IllegalStateException("Jellyseerr not initialized"))
		}

		client.getSonarrSettings()
	}

	override fun close() {
		httpClient?.close()
		httpClient = null
	}
}
