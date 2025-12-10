package org.jellyfin.androidtv.ui.jellyseerr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.repository.JellyseerrRepository
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrRequestDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrSessionExpiredException
import org.jellyfin.androidtv.data.service.jellyseerr.Seasons
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.jellyfin.androidtv.util.ErrorHandler
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class JellyseerrLoadingState {
	data object Idle : JellyseerrLoadingState()
	data object Loading : JellyseerrLoadingState()
	data class Success(val message: String = "") : JellyseerrLoadingState()
	data class Error(val message: String) : JellyseerrLoadingState()
	/**
	 * Session has expired and user needs to re-authenticate.
	 * This state is triggered when a 403 error is received from Jellyseerr.
	 */
	data class SessionExpired(val message: String = "Your Jellyseerr session has expired. Please sign in again.") : JellyseerrLoadingState()
}

/**
 * ViewModel for managing Jellyseerr integration UI
 */
class JellyseerrViewModel(
	private val jellyseerrRepository: JellyseerrRepository,
	private val jellyseerrPreferences: JellyseerrPreferences,
) : ViewModel() {

	private val _loadingState = MutableStateFlow<JellyseerrLoadingState>(JellyseerrLoadingState.Idle)
	val loadingState: StateFlow<JellyseerrLoadingState> = _loadingState.asStateFlow()

	private val _trendingMovies = MutableStateFlow<List<JellyseerrDiscoverItemDto>>(emptyList())
	val trendingMovies: StateFlow<List<JellyseerrDiscoverItemDto>> = _trendingMovies.asStateFlow()

	private val _trendingTv = MutableStateFlow<List<JellyseerrDiscoverItemDto>>(emptyList())
	val trendingTv: StateFlow<List<JellyseerrDiscoverItemDto>> = _trendingTv.asStateFlow()

	private val _trending = MutableStateFlow<List<JellyseerrDiscoverItemDto>>(emptyList())
	val trending: StateFlow<List<JellyseerrDiscoverItemDto>> = _trending.asStateFlow()

	private val _upcomingMovies = MutableStateFlow<List<JellyseerrDiscoverItemDto>>(emptyList())
	val upcomingMovies: StateFlow<List<JellyseerrDiscoverItemDto>> = _upcomingMovies.asStateFlow()

	private val _upcomingTv = MutableStateFlow<List<JellyseerrDiscoverItemDto>>(emptyList())
	val upcomingTv: StateFlow<List<JellyseerrDiscoverItemDto>> = _upcomingTv.asStateFlow()

	// Cache of blacklisted TMDB IDs
	private var blacklistedTmdbIds = setOf<Int>()

	/**
	 * Fetch and cache the blacklist
	 */
	private suspend fun fetchBlacklist() {
		val result = ErrorHandler.catchingWarning("fetch Jellyseerr blacklist") {
			jellyseerrRepository.getBlacklist()
		}
		if (result.isSuccess) {
			val blacklist = result.getOrNull()?.getOrNull()?.results ?: emptyList()
			blacklistedTmdbIds = blacklist.map { it.tmdbId }.toSet()
			Timber.d("Jellyseerr: Loaded ${blacklistedTmdbIds.size} blacklisted items")
		}
	}

	/**
	 * Filter out items that are in the blacklist
	 */
	private fun List<JellyseerrDiscoverItemDto>.filterBlacklist(): List<JellyseerrDiscoverItemDto> {
		return filter { item ->
			// Always block if in blacklist
			if (blacklistedTmdbIds.contains(item.id)) {
				val title = item.title ?: item.name ?: "Unknown"
				Timber.d("Jellyseerr Filter: Blocked '$title' (blacklisted)")
				return@filter false
			}
			true
		}
	}

	/**
	 * Filter out NSFW content if blocking is enabled
	 */
	private fun List<JellyseerrDiscoverItemDto>.filterNsfw(): List<JellyseerrDiscoverItemDto> {
		val blockNsfw = jellyseerrPreferences[JellyseerrPreferences.blockNsfw]
		
		return if (blockNsfw) {
			val filtered = filter { item ->
				// Always block if marked as adult by TMDB
				if (item.adult) {
					val title = item.title ?: item.name ?: "Unknown"
					Timber.d("Jellyseerr Filter: Blocked '$title' (marked as adult)")
					return@filter false
				}
				
				// Apply keyword filtering
				val displayTitle = (item.title ?: item.name ?: "").lowercase()
				val overview = (item.overview ?: "").lowercase()
				val combinedText = "$displayTitle $overview"
				
				// NSFW content keywords
				val matureKeywords = listOf(
					"\\bsex\\b", "sexual", "\\bporn\\b", "erotic", "\\bnude\\b", "nudity",
					"\\bxxx\\b", "adult film", "prostitute", "stripper", "\\bescort\\b",
					"seduction", "\\baffair\\b", "threesome", "\\borgy\\b", "kinky",
					"fetish", "\\bbdsm\\b", "dominatrix"
				)
				
				// Block if any mature keyword is found (using regex for word boundaries)
				val matchedKeyword = matureKeywords.firstOrNull { keyword ->
					combinedText.contains(Regex(keyword))
				}
				
				if (matchedKeyword != null) {
					val title = item.title ?: item.name ?: "Unknown"
					Timber.d("Jellyseerr Filter: Blocked '$title' (keyword: ${matchedKeyword.replace("\\\\b", "")})")
					return@filter false
				}
				
				true
			}
			
			val blockedCount = size - filtered.size
			if (blockedCount > 0) {
				Timber.d("Jellyseerr NSFW Filter: Blocked $blockedCount items total")
			}
			
			filtered
		} else {
			this
		}
	}

	private val _userRequests = MutableStateFlow<List<JellyseerrRequestDto>>(emptyList())
	val userRequests: StateFlow<List<JellyseerrRequestDto>> = _userRequests.asStateFlow()

	private val _searchResults = MutableStateFlow<List<JellyseerrDiscoverItemDto>>(emptyList())
	val searchResults: StateFlow<List<JellyseerrDiscoverItemDto>> = _searchResults.asStateFlow()

	val isAvailable: StateFlow<Boolean> = jellyseerrRepository.isAvailable

	init {
		// Auto-initialize from saved preferences when ViewModel is created
		viewModelScope.launch {
			val result = ErrorHandler.catching("initialize Jellyseerr repository") {
				jellyseerrRepository.ensureInitialized()
			}
			if (result.isSuccess) {
				Timber.d("JellyseerrViewModel: Repository initialized successfully")
			}
		}
	}

	fun initializeJellyseerr(serverUrl: String, apiKey: String) {
		viewModelScope.launch {
			_loadingState.emit(JellyseerrLoadingState.Loading)
			val result = ErrorHandler.catching("initialize Jellyseerr") {
				jellyseerrRepository.initialize(serverUrl, apiKey)
			}
			
			if (result.isSuccess && result.getOrNull()?.isSuccess == true) {
				_loadingState.emit(JellyseerrLoadingState.Success("Jellyseerr initialized successfully"))
				loadTrendingContent()
			} else {
				val errorMessage = result.getOrNull()?.exceptionOrNull()?.let { error ->
					ErrorHandler.getUserFriendlyMessage(error, "initialize Jellyseerr")
				} ?: ErrorHandler.getUserFriendlyMessage(
					result.exceptionOrNull() ?: Exception("Initialization failed")
				)
				_loadingState.emit(JellyseerrLoadingState.Error(errorMessage))
			}
		}
	}

	suspend fun loginWithJellyfin(username: String, password: String, jellyfinUrl: String, jellyseerrUrl: String): Result<org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrUserDto> {
		return jellyseerrRepository.loginWithJellyfin(username, password, jellyfinUrl, jellyseerrUrl)
	}

	suspend fun loginLocal(email: String, password: String, jellyseerrUrl: String): Result<org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrUserDto> {
		return jellyseerrRepository.loginLocal(email, password, jellyseerrUrl)
	}

	suspend fun regenerateApiKey(): Result<String> {
		return jellyseerrRepository.regenerateApiKey()
	}

	fun loadTrendingContent() {
		viewModelScope.launch {
			_loadingState.emit(JellyseerrLoadingState.Loading)
			try {
				// Fetch blacklist first
				fetchBlacklist()

				// Get fetch limit from preferences
				val itemsPerPage = jellyseerrPreferences[JellyseerrPreferences.fetchLimit].limit

				// Fetch multiple pages to get more content for searching
				// Filter out already-available content since users can watch those in the main Moonfin app
				val allTrending = mutableListOf<JellyseerrDiscoverItemDto>()
				val allTrendingMovies = mutableListOf<JellyseerrDiscoverItemDto>()
				val allTrendingTv = mutableListOf<JellyseerrDiscoverItemDto>()
				val allUpcomingMovies = mutableListOf<JellyseerrDiscoverItemDto>()
				val allUpcomingTv = mutableListOf<JellyseerrDiscoverItemDto>()
				var sessionExpired = false

				// Fetch first 3 pages to get enough content
				for (page in 1..3) {
					val offset = (page - 1) * itemsPerPage
					val trendingResult = jellyseerrRepository.getTrending(limit = itemsPerPage, offset = offset)
					val trendingMoviesResult = jellyseerrRepository.getTrendingMovies(limit = itemsPerPage, offset = offset)
					val trendingTvResult = jellyseerrRepository.getTrendingTv(limit = itemsPerPage, offset = offset)
					val upcomingMoviesResult = jellyseerrRepository.getUpcomingMovies(limit = itemsPerPage, offset = offset)
					val upcomingTvResult = jellyseerrRepository.getUpcomingTv(limit = itemsPerPage, offset = offset)

					// Check for session expiration (403 errors)
					val results = listOf(trendingResult, trendingMoviesResult, trendingTvResult, upcomingMoviesResult, upcomingTvResult)
					for (result in results) {
						if (result.isFailure && result.exceptionOrNull() is JellyseerrSessionExpiredException) {
							sessionExpired = true
							Timber.w("JellyseerrViewModel: Session expired detected during content fetch")
							break
						}
					}

					if (sessionExpired) break

					if (trendingResult.isSuccess) {
						allTrending.addAll(trendingResult.getOrNull()?.results ?: emptyList())
					}
					if (trendingMoviesResult.isSuccess) {
						allTrendingMovies.addAll(trendingMoviesResult.getOrNull()?.results ?: emptyList())
					}
					if (trendingTvResult.isSuccess) {
						allTrendingTv.addAll(trendingTvResult.getOrNull()?.results ?: emptyList())
					}
					if (upcomingMoviesResult.isSuccess) {
						allUpcomingMovies.addAll(upcomingMoviesResult.getOrNull()?.results ?: emptyList())
					}
					if (upcomingTvResult.isSuccess) {
						allUpcomingTv.addAll(upcomingTvResult.getOrNull()?.results ?: emptyList())
					}
				}

				// If session expired, emit SessionExpired state so UI can prompt re-authentication
				if (sessionExpired) {
					Timber.i("JellyseerrViewModel: Emitting SessionExpired state - user needs to re-authenticate")
					_loadingState.emit(JellyseerrLoadingState.SessionExpired())
					return@launch
				}

				if (allTrending.isNotEmpty() || allTrendingMovies.isNotEmpty() || allTrendingTv.isNotEmpty()) {
					// Filter out already-available content, blacklisted items, NSFW content, and prepare data
					val trending = allTrending
						.filterNot { it.isAvailable() }
						.filterNot { it.isBlacklisted() }
						.filterBlacklist()
						.filter { (it.mediaType ?: "").lowercase() in listOf("movie", "tv") }
						.filterNsfw()
					val trendingMovies = allTrendingMovies
						.filterNot { it.isAvailable() }
						.filterNot { it.isBlacklisted() }
						.filterBlacklist()
						.filter { (it.mediaType ?: "").lowercase() == "movie" }
						.filterNsfw()
					val trendingTv = allTrendingTv
						.filterNot { it.isAvailable() }
						.filterNot { it.isBlacklisted() }
						.filterBlacklist()
						.filter { (it.mediaType ?: "").lowercase() == "tv" }
						.filterNsfw()
					val upcomingMovies = allUpcomingMovies
						.filterNot { it.isAvailable() }
						.filterNot { it.isBlacklisted() }
						.filterBlacklist()
						.filter { (it.mediaType ?: "").lowercase() == "movie" }
						.filterNsfw()
					val upcomingTv = allUpcomingTv
						.filterNot { it.isAvailable() }
						.filterNot { it.isBlacklisted() }
						.filterBlacklist()
						.filter { (it.mediaType ?: "").lowercase() == "tv" }
						.filterNsfw()

					Timber.d("JellyseerrViewModel: Fetched trending: ${allTrending.size} (filtered: ${trending.size})")
					Timber.d("JellyseerrViewModel: Fetched trending movies: ${allTrendingMovies.size} (filtered: ${trendingMovies.size})")
					Timber.d("JellyseerrViewModel: Fetched trending TV: ${allTrendingTv.size} (filtered: ${trendingTv.size})")
					Timber.d("JellyseerrViewModel: Fetched upcoming movies: ${allUpcomingMovies.size} (filtered: ${upcomingMovies.size})")
					Timber.d("JellyseerrViewModel: Fetched upcoming TV: ${allUpcomingTv.size} (filtered: ${upcomingTv.size})")

					_trending.emit(trending)
					_trendingMovies.emit(trendingMovies)
					_trendingTv.emit(trendingTv)
					_upcomingMovies.emit(upcomingMovies)
					_upcomingTv.emit(upcomingTv)
					_loadingState.emit(JellyseerrLoadingState.Success())
				} else {
					_loadingState.emit(
						JellyseerrLoadingState.Error("Failed to load trending content")
					)
				}
			} catch (error: JellyseerrSessionExpiredException) {
				// Catch session expired exception and emit SessionExpired state
				Timber.w(error, "JellyseerrViewModel: Session expired exception caught")
				_loadingState.emit(JellyseerrLoadingState.SessionExpired())
			} catch (error: Exception) {
				val errorMessage = ErrorHandler.handle(error, "load trending content")
				_loadingState.emit(JellyseerrLoadingState.Error(errorMessage))
			}
		}
	}

	fun loadRequests() {
		viewModelScope.launch {
			loadRequestsSuspend()
		}
	}

	private suspend fun loadRequestsSuspend() {
		_loadingState.emit(JellyseerrLoadingState.Loading)
		try {
			Timber.d("JellyseerrViewModel: Starting loadRequests, isAvailable=${isAvailable.value}")
			
			// First, get the current user ID
			val currentUserResult = jellyseerrRepository.getCurrentUser()
			if (currentUserResult.isFailure) {
				val error = currentUserResult.exceptionOrNull()?.message ?: "Failed to get current user"
				Timber.e("JellyseerrViewModel: Error getting current user: $error")
				_loadingState.emit(JellyseerrLoadingState.Error(error))
				return
			}
			
			val currentUser = currentUserResult.getOrNull()!!
			Timber.d("JellyseerrViewModel: Current user ID: ${currentUser.id}")
			
			// Fetch requests filtered by current user ID using requestedBy parameter
			val result = jellyseerrRepository.getRequests(
				filter = "all",
				requestedBy = currentUser.id,
				limit = 100
			)
				
				if (result.isSuccess) {
					val userRequests = result.getOrNull()?.results ?: emptyList()
					Timber.d("JellyseerrViewModel: Fetched ${userRequests.size} requests for user ${currentUser.id}")
					
					// Enrich each request with full media details
					val enrichedRequests = userRequests.mapNotNull { request ->
						val tmdbId = request.media?.tmdbId
						if (tmdbId == null) {
							Timber.w("JellyseerrViewModel: Request ${request.id} has no tmdbId, skipping enrichment")
							return@mapNotNull request
						}
						
						// Fetch full movie or TV details based on media type
						val enrichedMedia = when (request.type) {
							"movie" -> {
								val result = jellyseerrRepository.getMovieDetails(tmdbId)
								if (result.isSuccess) {
									val movieDetails = result.getOrNull()
									request.media?.copy(
										title = movieDetails?.title,
										posterPath = movieDetails?.posterPath,
										backdropPath = movieDetails?.backdropPath,
										overview = movieDetails?.overview
									)
								} else {
									Timber.w("JellyseerrViewModel: Failed to fetch movie details for request ${request.id}, tmdbId: $tmdbId")
									request.media
								}
							}
							"tv" -> {
								val result = jellyseerrRepository.getTvDetails(tmdbId)
								if (result.isSuccess) {
									val tvDetails = result.getOrNull()
									request.media?.copy(
										name = tvDetails?.name ?: tvDetails?.title,
										posterPath = tvDetails?.posterPath,
										backdropPath = tvDetails?.backdropPath,
										overview = tvDetails?.overview
									)
								} else {
									Timber.w("JellyseerrViewModel: Failed to fetch TV details for request ${request.id}, tmdbId: $tmdbId")
									request.media
								}
							}
							else -> {
								Timber.w("JellyseerrViewModel: Unknown media type: ${request.type}")
								request.media
							}
						}
						
						request.copy(media = enrichedMedia)
					}
					
					enrichedRequests.forEach { request ->
						Timber.d("JellyseerrViewModel: Request ${request.id} - Type: ${request.type}, Status: ${request.status}, Media: ${request.media?.title ?: request.media?.name}, RequestedBy: ${request.requestedBy?.username}")
					}
					
					// Filter requests based on status and last modified date
					val filteredRequests = enrichedRequests.filter { request ->
						when (request.status) {
							1 -> true // Pending - always show
							2 -> true // Approved/Processing - always show  
							3 -> { // Declined - show only if modified within 3 days
								isWithinDays(request.updatedAt, 3)
							}
							4 -> { // Available - show only if modified within 3 days
								isWithinDays(request.updatedAt, 3)
							}
							else -> true // Unknown status - show it
						}
					}
					
			
			Timber.d("JellyseerrViewModel: Filtered ${enrichedRequests.size} requests to ${filteredRequests.size} after date filtering")
			_userRequests.emit(filteredRequests)
			_loadingState.emit(JellyseerrLoadingState.Success())
		} else {
			val error = result.exceptionOrNull()?.message ?: "Failed to load requests"
			Timber.e("JellyseerrViewModel: Error loading requests: $error")
			_loadingState.emit(JellyseerrLoadingState.Error(error))
		}
	} catch (error: Exception) {
		Timber.e(error, "Failed to load requests - Exception")
		_loadingState.emit(JellyseerrLoadingState.Error(error.message ?: "Unknown error"))
	}
}	/**
	 * Create a request for a specific media item
	 */
	fun createRequest(mediaId: Int, mediaType: String, seasons: Seasons? = null) {
		viewModelScope.launch {
			_loadingState.emit(JellyseerrLoadingState.Loading)
			try {
				val result = jellyseerrRepository.createRequest(mediaId, mediaType, seasons)
				if (result.isSuccess) {
					_loadingState.emit(JellyseerrLoadingState.Success("Request submitted successfully"))
					loadRequests()
				} else {
					_loadingState.emit(
						JellyseerrLoadingState.Error(result.exceptionOrNull()?.message ?: "Failed to create request")
					)
				}
			} catch (error: Exception) {
				Timber.e(error, "Failed to create request")
				_loadingState.emit(JellyseerrLoadingState.Error(error.message ?: "Unknown error"))
			}
		}
	}

	suspend fun getMovieDetails(tmdbId: Int) = jellyseerrRepository.getMovieDetails(tmdbId)

	suspend fun getTvDetails(tmdbId: Int) = jellyseerrRepository.getTvDetails(tmdbId)

	suspend fun getSimilarMovies(tmdbId: Int, page: Int = 1) = jellyseerrRepository.getSimilarMovies(tmdbId, page)

	suspend fun getSimilarTv(tmdbId: Int, page: Int = 1) = jellyseerrRepository.getSimilarTv(tmdbId, page)

	suspend fun getPersonDetails(personId: Int) = jellyseerrRepository.getPersonDetails(personId)

	suspend fun getPersonCombinedCredits(personId: Int) = jellyseerrRepository.getPersonCombinedCredits(personId)

	private suspend fun requestContent(
		mediaId: Int,
		mediaType: String,
		seasons: List<Int>?,
		is4k: Boolean = false
	) {
		Timber.d("JellyseerrViewModel: Requesting media - ID: $mediaId, Type: $mediaType, Seasons: $seasons, 4K: $is4k")
		
		// Convert seasons list to Seasons sealed class
		val seasonsParam = when {
			mediaType != "tv" -> null
			seasons == null -> Seasons.All
			else -> Seasons.List(seasons)
		}
		
		// Get custom profile settings from preferences
		val profileId = when {
			mediaType == "movie" && is4k -> jellyseerrPreferences[JellyseerrPreferences.fourKMovieProfileId]?.toIntOrNull()
			mediaType == "movie" && !is4k -> jellyseerrPreferences[JellyseerrPreferences.hdMovieProfileId]?.toIntOrNull()
			mediaType == "tv" && is4k -> jellyseerrPreferences[JellyseerrPreferences.fourKTvProfileId]?.toIntOrNull()
			mediaType == "tv" && !is4k -> jellyseerrPreferences[JellyseerrPreferences.hdTvProfileId]?.toIntOrNull()
			else -> null
		}
		
		val rootFolderId = when {
			mediaType == "movie" && is4k -> jellyseerrPreferences[JellyseerrPreferences.fourKMovieRootFolderId]?.toIntOrNull()
			mediaType == "movie" && !is4k -> jellyseerrPreferences[JellyseerrPreferences.hdMovieRootFolderId]?.toIntOrNull()
			mediaType == "tv" && is4k -> jellyseerrPreferences[JellyseerrPreferences.fourKTvRootFolderId]?.toIntOrNull()
			mediaType == "tv" && !is4k -> jellyseerrPreferences[JellyseerrPreferences.hdTvRootFolderId]?.toIntOrNull()
			else -> null
		}
		
		val serverId = when {
			mediaType == "movie" && is4k -> jellyseerrPreferences[JellyseerrPreferences.fourKMovieServerId]?.toIntOrNull()
			mediaType == "movie" && !is4k -> jellyseerrPreferences[JellyseerrPreferences.hdMovieServerId]?.toIntOrNull()
			mediaType == "tv" && is4k -> jellyseerrPreferences[JellyseerrPreferences.fourKTvServerId]?.toIntOrNull()
			mediaType == "tv" && !is4k -> jellyseerrPreferences[JellyseerrPreferences.hdTvServerId]?.toIntOrNull()
			else -> null
		}
		
		Timber.d("JellyseerrViewModel: Using custom profiles - profileId=$profileId, rootFolderId=$rootFolderId, serverId=$serverId")
		
		val result = jellyseerrRepository.createRequest(mediaId, mediaType, seasonsParam, is4k, profileId, rootFolderId, serverId)
		if (result.isFailure) {
			val error = result.exceptionOrNull()
			Timber.e(error, "Failed to request content")
			throw error ?: Exception("Unknown error while requesting content")
		}
		loadRequestsSuspend()
	}

	suspend fun requestMedia(
		item: JellyseerrDiscoverItemDto,
		seasons: List<Int>? = null,
		is4k: Boolean = false
	): Result<Unit> {
		return try {
			val mediaType = item.mediaType ?: return Result.failure(Exception("Unknown media type"))
			val mediaId = item.id
			requestContent(mediaId, mediaType, seasons, is4k)
			Result.success(Unit)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	/**
	 * Search for media items
	 */
	fun search(query: String, mediaType: String? = null) {
		viewModelScope.launch {
			if (query.isBlank()) {
				_searchResults.emit(emptyList())
				return@launch
			}

			_loadingState.emit(JellyseerrLoadingState.Loading)
			try {
				// Fetch blacklist first to ensure we have the latest blocked items
				fetchBlacklist()
				
				// Use fetch limit from preferences for search results
				val searchLimit = jellyseerrPreferences[JellyseerrPreferences.fetchLimit].limit
				val result = jellyseerrRepository.search(query, mediaType, limit = searchLimit)
			if (result.isSuccess) {
				val results = result.getOrNull()?.results ?: emptyList()
				Timber.d("Jellyseerr Search: Raw results count: ${results.size}")
				
				// Filter out already-available content, blacklisted items, and NSFW content from search results
				val filteredResults = results
					.filterNot { it.isAvailable() }
					.filterNot { it.isBlacklisted() }
					.filterBlacklist()
					.filterNsfw()
				
				Timber.d("Jellyseerr Search: Filtered results count: ${filteredResults.size}")
				if (filteredResults.isNotEmpty()) {
					Timber.d("Jellyseerr Search: Results include:")
					filteredResults.take(5).forEach { item ->
						val displayTitle = item.title ?: item.name ?: "Unknown"
						Timber.d("  - $displayTitle (Adult: ${item.adult})")
					}
				}
				
				_searchResults.emit(filteredResults)
				_loadingState.emit(JellyseerrLoadingState.Success())
				} else {
					_loadingState.emit(
						JellyseerrLoadingState.Error(result.exceptionOrNull()?.message ?: "Search failed")
					)
				}
			} catch (error: Exception) {
				Timber.e(error, "Search failed")
				_loadingState.emit(JellyseerrLoadingState.Error(error.message ?: "Unknown error"))
			}
		}
	}

	/**
	 * Check if a date string is within the specified number of days from now
	 */
	private fun isWithinDays(dateString: String?, days: Int): Boolean {
		if (dateString == null) return false
		
		return try {
			// Jellyseerr uses ISO 8601 format: "2020-09-12T10:00:27.000Z"
			val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
			val date = dateFormat.parse(dateString) ?: return false
			
			val now = Date()
			val diffInMillis = now.time - date.time
			val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)
			
			diffInDays <= days
		} catch (e: Exception) {
			Timber.w(e, "Failed to parse date: $dateString")
			false
		}
	}

	override fun onCleared() {
		super.onCleared()
		jellyseerrRepository.close()
	}
}
