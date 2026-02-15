package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrCreateRequestDto
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
import org.jellyfin.androidtv.data.service.jellyseerr.MoonfinLoginResponse
import org.jellyfin.androidtv.data.service.jellyseerr.MoonfinProxyConfig
import org.jellyfin.androidtv.data.service.jellyseerr.MoonfinStatusResponse
import org.jellyfin.androidtv.data.service.jellyseerr.Seasons
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

interface JellyseerrRepository {
	val isAvailable: StateFlow<Boolean>
	val isMoonfinMode: StateFlow<Boolean>

	suspend fun ensureInitialized()
	suspend fun initialize(serverUrl: String, apiKey: String): Result<Unit>
	suspend fun testConnection(): Result<Boolean>
	suspend fun getMovieDetails(tmdbId: Int): Result<JellyseerrMovieDetailsDto>
	suspend fun getTvDetails(tmdbId: Int): Result<JellyseerrTvDetailsDto>
	suspend fun loginWithJellyfin(username: String, password: String, jellyfinUrl: String, jellyseerrUrl: String): Result<JellyseerrUserDto>
	suspend fun loginLocal(email: String, password: String, jellyseerrUrl: String): Result<JellyseerrUserDto>
	suspend fun regenerateApiKey(): Result<String>
	suspend fun loginWithApiKey(apiKey: String, jellyseerrUrl: String): Result<JellyseerrUserDto>
	suspend fun isSessionValid(): Result<Boolean>
	suspend fun isSessionValidCached(): Boolean
	suspend fun getCurrentUser(): Result<JellyseerrUserDto>
	
	/** Get user-specific preferences (handles migration from global automatically) */
	suspend fun getPreferences(): JellyseerrPreferences?

	suspend fun getRequests(
		filter: String? = null,
		requestedBy: Int? = null,
		limit: Int = 50,
		offset: Int = 0,
	): Result<JellyseerrListResponse<JellyseerrRequestDto>>

	suspend fun createRequest(
		mediaId: Int,
		mediaType: String,
		seasons: Seasons? = null,
		is4k: Boolean = false,
		profileId: Int? = null,
		rootFolderId: Int? = null,
		serverId: Int? = null,
	): Result<JellyseerrRequestDto>

	suspend fun deleteRequest(requestId: Int): Result<Unit>

	suspend fun getTrendingMovies(limit: Int = 20, offset: Int = 0): Result<JellyseerrDiscoverPageDto>
	suspend fun getTrendingTv(limit: Int = 20, offset: Int = 0): Result<JellyseerrDiscoverPageDto>
	suspend fun getTrending(limit: Int = 20, offset: Int = 0): Result<JellyseerrDiscoverPageDto>
	suspend fun getTopMovies(limit: Int = 20, offset: Int = 0): Result<JellyseerrDiscoverPageDto>
	suspend fun getTopTv(limit: Int = 20, offset: Int = 0): Result<JellyseerrDiscoverPageDto>
	suspend fun getUpcomingMovies(limit: Int = 20, offset: Int = 0): Result<JellyseerrDiscoverPageDto>
	suspend fun getUpcomingTv(limit: Int = 20, offset: Int = 0): Result<JellyseerrDiscoverPageDto>

	suspend fun search(query: String, mediaType: String? = null, limit: Int = 20, offset: Int = 0): Result<JellyseerrDiscoverPageDto>
	suspend fun getSimilarMovies(tmdbId: Int, page: Int = 1): Result<JellyseerrDiscoverPageDto>
	suspend fun getSimilarTv(tmdbId: Int, page: Int = 1): Result<JellyseerrDiscoverPageDto>
	suspend fun getRecommendationsMovies(tmdbId: Int, page: Int = 1): Result<JellyseerrDiscoverPageDto>
	suspend fun getRecommendationsTv(tmdbId: Int, page: Int = 1): Result<JellyseerrDiscoverPageDto>
	suspend fun getPersonDetails(personId: Int): Result<JellyseerrPersonDetailsDto>
	suspend fun getPersonCombinedCredits(personId: Int): Result<JellyseerrPersonCombinedCreditsDto>
	suspend fun getGenreSliderMovies(): Result<List<JellyseerrGenreDto>>
	suspend fun getGenreSliderTv(): Result<List<JellyseerrGenreDto>>

	suspend fun discoverMovies(
		page: Int = 1,
		sortBy: String = "popularity.desc",
		genre: Int? = null,
		studio: Int? = null,
		keywords: Int? = null,
		language: String = "en"
	): Result<JellyseerrDiscoverPageDto>

	suspend fun discoverTv(
		page: Int = 1,
		sortBy: String = "popularity.desc",
		genre: Int? = null,
		network: Int? = null,
		keywords: Int? = null,
		language: String = "en"
	): Result<JellyseerrDiscoverPageDto>

	suspend fun getRadarrServers(): Result<List<JellyseerrServiceServerDto>>
	suspend fun getRadarrServerDetails(serverId: Int): Result<JellyseerrServiceServerDetailsDto>
	suspend fun getSonarrServers(): Result<List<JellyseerrServiceServerDto>>
	suspend fun getSonarrServerDetails(serverId: Int): Result<JellyseerrServiceServerDetailsDto>
	suspend fun getRadarrSettings(): Result<List<JellyseerrRadarrSettingsDto>>
	suspend fun getSonarrSettings(): Result<List<JellyseerrSonarrSettingsDto>>
	suspend fun logout()
	fun close()

	suspend fun configureWithMoonfin(jellyfinBaseUrl: String, jellyfinToken: String): Result<MoonfinStatusResponse>
	suspend fun checkMoonfinStatus(): Result<MoonfinStatusResponse>
	suspend fun loginWithMoonfin(username: String, password: String, authType: String): Result<MoonfinLoginResponse>
	suspend fun logoutMoonfin(): Result<Unit>
}

class JellyseerrRepositoryImpl(
	private val context: android.content.Context,
	private val globalPreferences: JellyseerrPreferences, // Global preferences (UI settings only)
	private val userRepository: UserRepository,
) : JellyseerrRepository, KoinComponent {
	private val api: ApiClient by inject()
	private var httpClient: JellyseerrHttpClient? = null
	private val _isAvailable = MutableStateFlow(false)
	override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()
	private val _isMoonfinMode = MutableStateFlow(false)
	override val isMoonfinMode: StateFlow<Boolean> = _isMoonfinMode.asStateFlow()
	private var initialized = false
	private var lastUserId: String? = null // Track which user we're initialized for
	
	// Session validity cache to prevent excessive login attempts
	private var lastSessionCheckTime: Long = 0
	private var lastSessionValid: Boolean = false
	
	companion object {
		private const val SESSION_CACHE_DURATION_MS = 5 * 60 * 1000L
	}

	private suspend fun <T> withClient(block: suspend (JellyseerrHttpClient) -> Result<T>): Result<T> {
		ensureInitialized()
		val client = httpClient ?: return Result.failure(
			IllegalStateException("HTTP client not initialized")
		)
		return block(client)
	}

	override suspend fun getPreferences(): JellyseerrPreferences? {
		val user = userRepository.currentUser.first { it != null }
		return user?.id?.let { userId ->
			// Use migration helper to ensure global settings are migrated to user-specific
			JellyseerrPreferences.migrateToUserPreferences(context, userId.toString())
		}
	}

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
				val userPrefs = getPreferences()
				val serverUrl = userPrefs?.get(JellyseerrPreferences.serverUrl) ?: ""
				val enabled = userPrefs?.get(JellyseerrPreferences.enabled) ?: false
				
				val user = withTimeoutOrNull(5000L) {
					userRepository.currentUser.first { it != null }
				}
				
				if (user == null) {
					_isAvailable.emit(false)
					initialized = true
					return@withContext
				}
				
				JellyseerrHttpClient.switchCookieStorage(user.id.toString())
				lastUserId = user.id.toString()
				
				val storedApiKey = userPrefs?.get(JellyseerrPreferences.apiKey) ?: ""
				val authMethod = userPrefs?.get(JellyseerrPreferences.authMethod) ?: ""
				val moonfinMode = userPrefs?.get(JellyseerrPreferences.moonfinMode) ?: false

				if (moonfinMode) {
					val baseUrl = api.baseUrl
					val token = api.accessToken
					if (!baseUrl.isNullOrBlank() && !token.isNullOrBlank()) {
						Timber.d("Jellyseerr: Auto-initializing in Moonfin proxy mode for user $lastUserId")
						val proxyConfig = MoonfinProxyConfig(
							jellyfinBaseUrl = baseUrl,
							jellyfinToken = token
						)
						val result = initialize(baseUrl, "")
						if (result.isSuccess) {
							httpClient?.proxyConfig = proxyConfig
							_isMoonfinMode.emit(true)
							val statusResult = httpClient?.getMoonfinStatus()
							if (statusResult != null && statusResult.isSuccess) {
								val status = statusResult.getOrNull()
								if (status?.authenticated == true) {
									// Trust the Status check like Tizen does.
									// Do NOT call getCurrentUser() through the proxy here - it can
									// cause Express.js cookie rotation which the plugin doesn't save,
									// invalidating the session for subsequent API calls.
									Timber.d("Jellyseerr: Moonfin session authenticated (via Status)")
									_isAvailable.emit(true)
								} else {
									Timber.d("Jellyseerr: Moonfin session not authenticated, user needs to login")
									_isAvailable.emit(false)
								}
							} else {
								_isAvailable.emit(false)
								Timber.w("Jellyseerr: Failed to check Moonfin status")
							}
						} else {
							_isAvailable.emit(false)
							Timber.w("Jellyseerr: Failed to initialize for Moonfin proxy")
						}
					} else {
						_isAvailable.emit(false)
						Timber.w("Jellyseerr: Moonfin mode enabled but no Jellyfin API credentials")
					}
					initialized = true
					return@withContext
				}

				if (enabled && serverUrl.isNotEmpty()) {
					Timber.d("Jellyseerr: Auto-initializing from saved preferences for user $lastUserId")
					
					if (storedApiKey.isNotEmpty()) {
						val result = initialize(serverUrl, storedApiKey)
						if (result.isSuccess) {
							_isAvailable.emit(true)
						} else {
							_isAvailable.emit(false)
							Timber.w("Jellyseerr: Failed to auto-initialize with API key")
						}
					} else if (authMethod == "jellyfin" || authMethod == "local") {
						val result = initialize(serverUrl, "")
						if (result.isSuccess) {
							val sessionValid = isSessionValid().getOrElse { false }
							_isAvailable.emit(sessionValid)
							if (!sessionValid) {
								Timber.w("Jellyseerr: Session expired, user needs to re-authenticate")
							}
						} else {
							_isAvailable.emit(false)
							Timber.w("Jellyseerr: Failed to auto-initialize for session check")
						}
					}
				} else {
					_isAvailable.emit(false)
				}
				initialized = true
			} catch (e: Exception) {
				Timber.e(e, "Jellyseerr: Failed to auto-initialize")
				_isAvailable.emit(false)
				initialized = true
			}
		}
	}

	override suspend fun initialize(serverUrl: String, apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
		try {
			httpClient?.close()
			httpClient = JellyseerrHttpClient(context, serverUrl, apiKey)
			initialized = true
			Result.success(Unit)
		} catch (e: Exception) {
			Timber.e(e, "Jellyseerr: Failed to initialize HTTP client")
			Result.failure(e)
		}
	}

	private fun invalidateSessionCache() {
		lastSessionCheckTime = 0
		lastSessionValid = false
	}

	override suspend fun testConnection(): Result<Boolean> = withClient { client ->
		client.testConnection().fold(
			onSuccess = { Result.success(true) },
			onFailure = { Result.failure(it) }
		)
	}

	override suspend fun isSessionValidCached(): Boolean {
		val currentTime = System.currentTimeMillis()
		
		// Return cached result if it's still valid and not too old
		if ((currentTime - lastSessionCheckTime) < SESSION_CACHE_DURATION_MS && lastSessionValid) {
			return lastSessionValid
		}

		// Check session validity and cache the result
		val isValid = isSessionValid().getOrElse { false }
		lastSessionCheckTime = currentTime
		lastSessionValid = isValid
		
		return isValid
	}

	override suspend fun isSessionValid(): Result<Boolean> = withClient { client ->
		client.getCurrentUser()
			.fold(
				onSuccess = { Result.success(true) },
				onFailure = { Result.success(false) }
			)
	}

	override suspend fun getCurrentUser(): Result<JellyseerrUserDto> = withClient { it.getCurrentUser() }

	override suspend fun getMovieDetails(tmdbId: Int): Result<JellyseerrMovieDetailsDto> = withClient { it.getMovieDetails(tmdbId) }

	override suspend fun getTvDetails(tmdbId: Int): Result<JellyseerrTvDetailsDto> = withClient { it.getTvDetails(tmdbId) }

	override suspend fun getRequests(
		filter: String?,
		requestedBy: Int?,
		limit: Int,
		offset: Int,
	): Result<JellyseerrListResponse<JellyseerrRequestDto>> = withClient { client ->
		client.getRequests(
			sort = "updated",
			filter = filter,
			requestedBy = requestedBy,
			requestType = null,
			limit = limit,
			offset = offset
		)
	}

	override suspend fun createRequest(
		mediaId: Int,
		mediaType: String,
		seasons: Seasons?,
		is4k: Boolean,
		profileId: Int?,
		rootFolderId: Int?,
		serverId: Int?,
	): Result<JellyseerrRequestDto> = withClient { client ->
		val requestData = JellyseerrCreateRequestDto(
			mediaId = mediaId,
			mediaType = mediaType,
			seasons = seasons,
			is4k = is4k,
			profileId = profileId,
			rootFolderId = rootFolderId,
			serverId = serverId
		)
		
		client.createRequest(
			mediaId = requestData.mediaId,
			mediaType = requestData.mediaType,
			seasons = requestData.seasons,
			is4k = requestData.is4k,
			profileId = requestData.profileId,
			rootFolderId = requestData.rootFolderId,
			serverId = requestData.serverId
		)
	}

	override suspend fun deleteRequest(requestId: Int): Result<Unit> = withClient { it.deleteRequest(requestId) }

	override suspend fun getTrendingMovies(limit: Int, offset: Int): Result<JellyseerrDiscoverPageDto> = withClient { it.getTrendingMovies(limit, offset) }

	override suspend fun getTrendingTv(limit: Int, offset: Int): Result<JellyseerrDiscoverPageDto> = withClient { it.getTrendingTv(limit, offset) }

	override suspend fun getTrending(limit: Int, offset: Int): Result<JellyseerrDiscoverPageDto> = withClient { it.getTrending(limit, offset) }

	override suspend fun getTopMovies(limit: Int, offset: Int): Result<JellyseerrDiscoverPageDto> = withClient { it.getTopMovies(limit, offset) }

	override suspend fun getTopTv(limit: Int, offset: Int): Result<JellyseerrDiscoverPageDto> = withClient { it.getTopTv(limit, offset) }

	override suspend fun getUpcomingMovies(limit: Int, offset: Int): Result<JellyseerrDiscoverPageDto> = withClient { it.getUpcomingMovies(limit, offset) }

	override suspend fun getUpcomingTv(limit: Int, offset: Int): Result<JellyseerrDiscoverPageDto> = withClient { it.getUpcomingTv(limit, offset) }

	override suspend fun search(
		query: String,
		mediaType: String?,
		limit: Int,
		offset: Int,
	): Result<JellyseerrDiscoverPageDto> = withClient { it.search(query, mediaType, limit, offset) }

	override suspend fun getSimilarMovies(tmdbId: Int, page: Int): Result<JellyseerrDiscoverPageDto> = withClient { it.getSimilarMovies(tmdbId, page) }

	override suspend fun getSimilarTv(tmdbId: Int, page: Int): Result<JellyseerrDiscoverPageDto> = withClient { it.getSimilarTv(tmdbId, page) }

	override suspend fun getRecommendationsMovies(tmdbId: Int, page: Int): Result<JellyseerrDiscoverPageDto> = withClient { it.getRecommendationsMovies(tmdbId, page) }

	override suspend fun getRecommendationsTv(tmdbId: Int, page: Int): Result<JellyseerrDiscoverPageDto> = withClient { it.getRecommendationsTv(tmdbId, page) }

	override suspend fun getPersonDetails(personId: Int): Result<JellyseerrPersonDetailsDto> = withClient { it.getPersonDetails(personId) }

	override suspend fun getPersonCombinedCredits(personId: Int): Result<JellyseerrPersonCombinedCreditsDto> = withClient { it.getPersonCombinedCredits(personId) }

	override suspend fun getGenreSliderMovies(): Result<List<JellyseerrGenreDto>> = withClient { it.getGenreSliderMovies() }

	override suspend fun getGenreSliderTv(): Result<List<JellyseerrGenreDto>> = withClient { it.getGenreSliderTv() }

	override suspend fun discoverMovies(
		page: Int,
		sortBy: String,
		genre: Int?,
		studio: Int?,
		keywords: Int?,
		language: String
	): Result<JellyseerrDiscoverPageDto> = withClient { it.discoverMovies(page, sortBy, genre, studio, keywords, language) }

	override suspend fun discoverTv(
		page: Int,
		sortBy: String,
		genre: Int?,
		network: Int?,
		keywords: Int?,
		language: String
	): Result<JellyseerrDiscoverPageDto> = withClient { it.discoverTv(page, sortBy, genre, network, keywords, language) }

	override suspend fun getRadarrServers(): Result<List<JellyseerrServiceServerDto>> = withClient { it.getRadarrServers() }

	override suspend fun getRadarrServerDetails(serverId: Int): Result<JellyseerrServiceServerDetailsDto> = withClient { it.getRadarrServerDetails(serverId) }

	override suspend fun getSonarrServers(): Result<List<JellyseerrServiceServerDto>> = withClient { it.getSonarrServers() }

	override suspend fun getSonarrServerDetails(serverId: Int): Result<JellyseerrServiceServerDetailsDto> = withClient { it.getSonarrServerDetails(serverId) }

	override suspend fun getRadarrSettings(): Result<List<JellyseerrRadarrSettingsDto>> = withClient { it.getRadarrSettings() }

	override suspend fun getSonarrSettings(): Result<List<JellyseerrSonarrSettingsDto>> = withClient { it.getSonarrSettings() }

	override suspend fun loginWithJellyfin(
		username: String, 
		password: String, 
		jellyfinUrl: String, 
		jellyseerrUrl: String
	): Result<JellyseerrUserDto> = withContext(Dispatchers.IO) {
		val currentUserId = userRepository.currentUser.value?.id?.toString()
		if (currentUserId.isNullOrEmpty()) {
			return@withContext Result.failure(IllegalStateException("No active Jellyfin user"))
		}

		JellyseerrHttpClient.switchCookieStorage(currentUserId)
		
		val userPrefs = getPreferences()
		userPrefs?.apply {
			set(JellyseerrPreferences.authMethod, "jellyfin")
			set(JellyseerrPreferences.serverUrl, jellyseerrUrl)
		}

		initialize(jellyseerrUrl, "")
		
		val client = httpClient ?: return@withContext Result.failure(
			IllegalStateException("Failed to initialize HTTP client")
		)
		
		val result = client.loginJellyfin(username, password, jellyfinUrl)
		result.onSuccess { user ->
			userPrefs?.apply {
				set(JellyseerrPreferences.enabled, true)
				set(JellyseerrPreferences.lastConnectionSuccess, true)
			}
			_isAvailable.emit(true)
			
			// Auto-generate API key if the user prefers it
			if (globalPreferences[JellyseerrPreferences.autoGenerateApiKey] == true) {
				regenerateApiKey().onSuccess { apiKey ->
					userPrefs?.apply {
						set(JellyseerrPreferences.apiKey, apiKey)
						set(JellyseerrPreferences.authMethod, "jellyfin-apikey")
					}
				}
			}
		}.onFailure { error ->
			userPrefs?.apply {
				set(JellyseerrPreferences.lastConnectionSuccess, false)
			}
			_isAvailable.emit(false)
		}
		
		result
	}

	override suspend fun loginLocal(
		email: String, 
		password: String, 
		jellyseerrUrl: String
	): Result<JellyseerrUserDto> = withContext(Dispatchers.IO) {
		val currentUserId = userRepository.currentUser.value?.id?.toString()
		if (currentUserId.isNullOrEmpty()) {
			return@withContext Result.failure(IllegalStateException("No active Jellyfin user"))
		}

		JellyseerrHttpClient.switchCookieStorage(currentUserId)
		
		val userPrefs = getPreferences()
		userPrefs?.apply {
			set(JellyseerrPreferences.authMethod, "local")
			set(JellyseerrPreferences.localEmail, email)
			set(JellyseerrPreferences.localPassword, password)
			set(JellyseerrPreferences.serverUrl, jellyseerrUrl)
		}

		initialize(jellyseerrUrl, "")
		
		val client = httpClient ?: return@withContext Result.failure(
			IllegalStateException("Failed to initialize HTTP client")
		)
		
		val result = client.loginLocal(email, password)
		result.onSuccess { user ->
			userPrefs?.apply {
				set(JellyseerrPreferences.enabled, true)
				set(JellyseerrPreferences.lastConnectionSuccess, true)
			}
			_isAvailable.emit(true)
			
			// Auto-generate API key if the user prefers it
			if (globalPreferences[JellyseerrPreferences.autoGenerateApiKey] == true) {
				regenerateApiKey().onSuccess { apiKey ->
					userPrefs?.apply {
						set(JellyseerrPreferences.apiKey, apiKey)
						set(JellyseerrPreferences.authMethod, "local-apikey")
					}
				}
			}
		}.onFailure { error ->
			userPrefs?.apply {
				set(JellyseerrPreferences.lastConnectionSuccess, false)
			}
			_isAvailable.emit(false)
		}
		
		result
	}

	override suspend fun regenerateApiKey(): Result<String> = withContext(Dispatchers.IO) {
		ensureInitialized()
		
		val client = httpClient ?: return@withContext Result.failure(
			IllegalStateException("HTTP client not initialized")
		)
		
		val result = client.regenerateApiKey()
		result.onSuccess { apiKey ->
			val userPrefs = getPreferences()
			userPrefs?.apply {
				set(JellyseerrPreferences.apiKey, apiKey)
				set(JellyseerrPreferences.authMethod, "jellyfin-apikey")
			}
		}.onFailure { error ->
			Timber.e(error, "Jellyseerr: Failed to regenerate API key")
		}
		
		result
	}

	override suspend fun loginWithApiKey(apiKey: String, jellyseerrUrl: String): Result<JellyseerrUserDto> = withContext(Dispatchers.IO) {
		val currentUserId = userRepository.currentUser.value?.id?.toString()
		if (currentUserId.isNullOrEmpty()) {
			return@withContext Result.failure(IllegalStateException("No active Jellyfin user"))
		}

		JellyseerrHttpClient.switchCookieStorage(currentUserId)
		
		val userPrefs = getPreferences()
		userPrefs?.apply {
			set(JellyseerrPreferences.authMethod, "apikey")
			set(JellyseerrPreferences.apiKey, apiKey)
			set(JellyseerrPreferences.serverUrl, jellyseerrUrl)
		}

		initialize(jellyseerrUrl, apiKey)
		
		val client = httpClient ?: return@withContext Result.failure(
			IllegalStateException("Failed to initialize HTTP client")
		)
		
		val result = client.getCurrentUser() // Verify API key works
		result.onSuccess { user ->
			userPrefs?.apply {
				set(JellyseerrPreferences.enabled, true)
				set(JellyseerrPreferences.lastConnectionSuccess, true)
			}
			_isAvailable.emit(true)
		}.onFailure { error ->
			userPrefs?.apply {
				set(JellyseerrPreferences.lastConnectionSuccess, false)
			}
			_isAvailable.emit(false)
		}
		
		result
	}

	override suspend fun configureWithMoonfin(
		jellyfinBaseUrl: String,
		jellyfinToken: String,
	): Result<MoonfinStatusResponse> = withContext(Dispatchers.IO) {
		val currentUserId = userRepository.currentUser.value?.id?.toString()
		if (currentUserId.isNullOrEmpty()) {
			return@withContext Result.failure(IllegalStateException("No active Jellyfin user"))
		}

		JellyseerrHttpClient.switchCookieStorage(currentUserId)
		lastUserId = currentUserId

		val proxyConfig = MoonfinProxyConfig(
			jellyfinBaseUrl = jellyfinBaseUrl,
			jellyfinToken = jellyfinToken
		)

		initialize(jellyfinBaseUrl, "")

		val client = httpClient ?: return@withContext Result.failure(
			IllegalStateException("Failed to initialize HTTP client")
		)

		client.proxyConfig = proxyConfig

		val statusResult = client.getMoonfinStatus()
		statusResult.onSuccess { status ->
			val userPrefs = getPreferences()
			userPrefs?.apply {
				set(JellyseerrPreferences.moonfinMode, true)
				set(JellyseerrPreferences.enabled, true)
				set(JellyseerrPreferences.authMethod, "moonfin")
			}
			_isMoonfinMode.emit(true)
			if (status.authenticated) {
				// Trust the Status check like Tizen does.
				// Do NOT call getCurrentUser() through the proxy here - it can
				// cause Express.js cookie rotation which the plugin doesn't save,
				// invalidating the session for subsequent API calls and destroying
				// sessions on other devices.
				Timber.d("Jellyseerr: Moonfin session authenticated in configureWithMoonfin")
				userPrefs?.apply {
					set(JellyseerrPreferences.moonfinJellyseerrUserId, status.jellyseerrUserId?.toString() ?: "")
				}
				_isAvailable.emit(true)
			} else {
				Timber.d("Jellyseerr: Moonfin session not authenticated, user needs to login")
				_isAvailable.emit(false)
			}
		}.onFailure {
			_isAvailable.emit(false)
		}

		statusResult
	}

	override suspend fun checkMoonfinStatus(): Result<MoonfinStatusResponse> = withClient { client ->
		client.getMoonfinStatus()
	}

	override suspend fun loginWithMoonfin(
		username: String,
		password: String,
		authType: String,
	): Result<MoonfinLoginResponse> = withContext(Dispatchers.IO) {
		ensureInitialized()

		val client = httpClient ?: return@withContext Result.failure(
			IllegalStateException("HTTP client not initialized")
		)

		if (!client.isProxyMode) {
			return@withContext Result.failure(IllegalStateException("Not in Moonfin proxy mode"))
		}

		val result = client.moonfinLogin(username, password, authType)
		result.onSuccess { response ->
			if (response.success) {
				val userPrefs = getPreferences()
				userPrefs?.apply {
					set(JellyseerrPreferences.moonfinDisplayName, response.displayName ?: "")
					set(JellyseerrPreferences.moonfinJellyseerrUserId, response.jellyseerrUserId?.toString() ?: "")
				}
				_isAvailable.emit(true)
				Timber.i("Jellyseerr: Moonfin login successful")
			}
		}

		result
	}

	override suspend fun logoutMoonfin(): Result<Unit> = withContext(Dispatchers.IO) {
		val client = httpClient
		if (client != null && client.isProxyMode) {
			client.moonfinLogout()
		}

		val userPrefs = getPreferences()
		userPrefs?.apply {
			set(JellyseerrPreferences.moonfinMode, false)
			set(JellyseerrPreferences.moonfinDisplayName, "")
			set(JellyseerrPreferences.moonfinJellyseerrUserId, "")
			set(JellyseerrPreferences.enabled, false)
			set(JellyseerrPreferences.authMethod, "")
		}

		httpClient?.proxyConfig = null
		_isMoonfinMode.emit(false)
		_isAvailable.emit(false)
		initialized = false

		Result.success(Unit)
	}

	override suspend fun logout() {
		if (httpClient?.isProxyMode == true) {
			logoutMoonfin()
			return
		}

		val userPrefs = getPreferences()
		userPrefs?.apply {
			set(JellyseerrPreferences.serverUrl, "")
			set(JellyseerrPreferences.enabled, false)
			set(JellyseerrPreferences.localEmail, "")
			set(JellyseerrPreferences.localPassword, "")
			set(JellyseerrPreferences.apiKey, "")
			set(JellyseerrPreferences.authMethod, "")
		}
		
		httpClient?.close()
		httpClient = null
		initialized = false
		lastUserId = null
		
		_isAvailable.emit(false)
	}

	override fun close() {
		httpClient?.close()
		httpClient = null
	}
}
