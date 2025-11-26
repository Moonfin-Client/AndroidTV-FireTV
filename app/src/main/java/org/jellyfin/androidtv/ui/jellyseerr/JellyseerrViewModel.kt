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
import org.jellyfin.androidtv.data.service.jellyseerr.Seasons
import org.jellyfin.androidtv.preference.JellyseerrPreferences
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

	private val _userRequests = MutableStateFlow<List<JellyseerrRequestDto>>(emptyList())
	val userRequests: StateFlow<List<JellyseerrRequestDto>> = _userRequests.asStateFlow()

	private val _searchResults = MutableStateFlow<List<JellyseerrDiscoverItemDto>>(emptyList())
	val searchResults: StateFlow<List<JellyseerrDiscoverItemDto>> = _searchResults.asStateFlow()

	val isAvailable: StateFlow<Boolean> = jellyseerrRepository.isAvailable

	init {
		// Auto-initialize from saved preferences when ViewModel is created
		viewModelScope.launch {
			try {
				jellyseerrRepository.ensureInitialized()
				Timber.d("JellyseerrViewModel: Repository initialized successfully")
			} catch (error: Exception) {
				Timber.e(error, "JellyseerrViewModel: Failed to initialize repository")
			}
		}
	}

	fun initializeJellyseerr(serverUrl: String, apiKey: String) {
		viewModelScope.launch {
			_loadingState.emit(JellyseerrLoadingState.Loading)
			try {
				val result = jellyseerrRepository.initialize(serverUrl, apiKey)
				if (result.isSuccess) {
					_loadingState.emit(JellyseerrLoadingState.Success("Jellyseerr initialized successfully"))
					loadTrendingContent()
				} else {
					_loadingState.emit(
						JellyseerrLoadingState.Error(result.exceptionOrNull()?.message ?: "Initialization failed")
					)
				}
			} catch (error: Exception) {
				Timber.e(error, "Failed to initialize Jellyseerr")
				_loadingState.emit(JellyseerrLoadingState.Error(error.message ?: "Unknown error"))
			}
		}
	}

	suspend fun loginWithJellyfin(username: String, password: String, jellyfinUrl: String, jellyseerrUrl: String): Result<org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrUserDto> {
		return jellyseerrRepository.loginWithJellyfin(username, password, jellyfinUrl, jellyseerrUrl)
	}

	fun loadTrendingContent() {
		viewModelScope.launch {
			_loadingState.emit(JellyseerrLoadingState.Loading)
		try {
			// Get fetch limit from preferences
			val itemsPerPage = jellyseerrPreferences[JellyseerrPreferences.fetchLimit].limit
			
			// Fetch multiple pages to get more content for searching
			// Filter out already-available content since users can watch those in the main Moonfin app
			val allTrending = mutableListOf<JellyseerrDiscoverItemDto>()
			val allTrendingMovies = mutableListOf<JellyseerrDiscoverItemDto>()
			val allTrendingTv = mutableListOf<JellyseerrDiscoverItemDto>()
			val allUpcomingMovies = mutableListOf<JellyseerrDiscoverItemDto>()
			val allUpcomingTv = mutableListOf<JellyseerrDiscoverItemDto>()
			var hasPermissionError = false
			
			// Fetch first 3 pages to get enough content
				for (page in 1..3) {
				val offset = (page - 1) * itemsPerPage
				val trendingResult = jellyseerrRepository.getTrending(limit = itemsPerPage, offset = offset)
				val trendingMoviesResult = jellyseerrRepository.getTrendingMovies(limit = itemsPerPage, offset = offset)
				val trendingTvResult = jellyseerrRepository.getTrendingTv(limit = itemsPerPage, offset = offset)
				val upcomingMoviesResult = jellyseerrRepository.getUpcomingMovies(limit = itemsPerPage, offset = offset)
				val upcomingTvResult = jellyseerrRepository.getUpcomingTv(limit = itemsPerPage, offset = offset)					// Check for 403 permission errors
					if (trendingResult.isFailure && trendingResult.exceptionOrNull()?.message?.contains("403") == true) {
						hasPermissionError = true
					}
					
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

			if (allTrending.isNotEmpty() || allTrendingMovies.isNotEmpty() || allTrendingTv.isNotEmpty()) {
				// Filter out already-available content and prepare data
				val trending = allTrending
					.filterNot { it.isAvailable() }
					.filter { (it.mediaType ?: "").lowercase() in listOf("movie", "tv") }
				val trendingMovies = allTrendingMovies
					.filterNot { it.isAvailable() }
					.filter { (it.mediaType ?: "").lowercase() == "movie" }
				val trendingTv = allTrendingTv
					.filterNot { it.isAvailable() }
					.filter { (it.mediaType ?: "").lowercase() == "tv" }
				val upcomingMovies = allUpcomingMovies
					.filterNot { it.isAvailable() }
					.filter { (it.mediaType ?: "").lowercase() == "movie" }
				val upcomingTv = allUpcomingTv
					.filterNot { it.isAvailable() }
					.filter { (it.mediaType ?: "").lowercase() == "tv" }
				
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
				} else if (hasPermissionError) {
					val errorMessage = "Permission Denied: Your Jellyfin account needs Jellyseerr permissions.\n\n" +
						"To fix this:\n" +
						"1. Open Jellyseerr web UI (http://your-server:5055)\n" +
						"2. Go to Settings → Users\n" +
						"3. Find your Jellyfin account\n" +
						"4. Enable 'REQUEST' permission\n" +
						"5. Restart this app"
					_loadingState.emit(JellyseerrLoadingState.Error(errorMessage))
				} else {
					_loadingState.emit(
						JellyseerrLoadingState.Error("Failed to load trending content")
					)
				}
			} catch (error: Exception) {
				Timber.e(error, "Failed to load trending content")
				val errorMessage = if (error.message?.contains("403") == true) {
					"Permission Denied: Your Jellyfin account needs Jellyseerr permissions.\n\n" +
						"To fix this:\n" +
						"1. Open Jellyseerr web UI (http://your-server:5055)\n" +
						"2. Go to Settings → Users\n" +
						"3. Find your Jellyfin account\n" +
						"4. Enable 'REQUEST' permission\n" +
						"5. Restart this app"
				} else {
					error.message ?: "Unknown error"
				}
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
		
		val result = jellyseerrRepository.createRequest(mediaId, mediaType, seasonsParam, is4k)
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
				// Use fetch limit from preferences for search results
				val searchLimit = jellyseerrPreferences[JellyseerrPreferences.fetchLimit].limit
				val result = jellyseerrRepository.search(query, mediaType, limit = searchLimit)
				if (result.isSuccess) {
					val results = result.getOrNull()?.results ?: emptyList()
					// Filter out already-available content from search results
					val filteredResults = results.filterNot { it.isAvailable() }
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
