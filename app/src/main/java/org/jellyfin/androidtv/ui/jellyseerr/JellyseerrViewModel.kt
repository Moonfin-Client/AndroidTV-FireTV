package org.jellyfin.androidtv.ui.jellyseerr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.repository.JellyseerrRepository
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrGenreDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrMediaDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrNetworkDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrRequestDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrStudioDto
import org.jellyfin.androidtv.data.service.jellyseerr.Seasons
import org.jellyfin.androidtv.constant.JellyseerrFetchLimit
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
}

class JellyseerrViewModel(
	private val jellyseerrRepository: JellyseerrRepository,
) : ViewModel() {

	// Cache for user preferences (loaded asynchronously)
	private var userPreferences: JellyseerrPreferences? = null
	
	private suspend fun getPreferences(): JellyseerrPreferences? {
		if (userPreferences == null) {
			userPreferences = jellyseerrRepository.getPreferences()
		}
		return userPreferences
	}

	companion object {
		// Popular TV networks (from Seerr - using duotone filtered URLs)
		val POPULAR_NETWORKS = listOf(
			JellyseerrNetworkDto(id = 213, name = "Netflix", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/wwemzKWzjKYJFfCeiB57q3r4Bcm.png"),
			JellyseerrNetworkDto(id = 2739, name = "Disney+", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/gJ8VX6JSu3ciXHuC2dDGAo2lvwM.png"),
			JellyseerrNetworkDto(id = 1024, name = "Prime Video", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ifhbNuuVnlwYy5oXA5VIb2YR8AZ.png"),
			JellyseerrNetworkDto(id = 2552, name = "Apple TV+", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/4KAy34EHvRM25Ih8wb82AuGU7zJ.png"),
			JellyseerrNetworkDto(id = 453, name = "Hulu", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/pqUTCleNUiTLAVlelGxUgWn1ELh.png"),
			JellyseerrNetworkDto(id = 49, name = "HBO", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/tuomPhY2UtuPTqqFnKMVHvSb724.png"),
			JellyseerrNetworkDto(id = 4353, name = "Discovery+", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1D1bS3Dyw4ScYnFWTlBOvJXC3nb.png"),
			JellyseerrNetworkDto(id = 2, name = "ABC", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ndAvF4JLsliGreX87jAc9GdjmJY.png"),
			JellyseerrNetworkDto(id = 19, name = "FOX", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1DSpHrWyOORkL9N2QHX7Adt31mQ.png"),
			JellyseerrNetworkDto(id = 359, name = "Cinemax", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/6mSHSquNpfLgDdv6VnOOvC5Uz2h.png"),
			JellyseerrNetworkDto(id = 174, name = "AMC", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/pmvRmATOCaDykE6JrVoeYxlFHw3.png"),
			JellyseerrNetworkDto(id = 67, name = "Showtime", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/Allse9kbjiP6ExaQrnSpIhkurEi.png"),
			JellyseerrNetworkDto(id = 318, name = "Starz", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/8GJjw3HHsAJYwIWKIPBPfqMxlEa.png"),
			JellyseerrNetworkDto(id = 71, name = "The CW", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ge9hzeaU7nMtQ4PjkFlc68dGAJ9.png"),
			JellyseerrNetworkDto(id = 6, name = "NBC", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/o3OedEP0f9mfZr33jz2BfXOUK5.png"),
			JellyseerrNetworkDto(id = 16, name = "CBS", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/nm8d7P7MJNiBLdgIzUK0gkuEA4r.png"),
			JellyseerrNetworkDto(id = 4330, name = "Paramount+", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/fi83B1oztoS47xxcemFdPMhIzK.png"),
			JellyseerrNetworkDto(id = 4, name = "BBC One", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/mVn7xESaTNmjBUyUtGNvDQd3CT1.png"),
			JellyseerrNetworkDto(id = 56, name = "Cartoon Network", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/c5OC6oVCg6QP4eqzW6XIq17CQjI.png"),
			JellyseerrNetworkDto(id = 80, name = "Adult Swim", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/9AKyspxVzywuaMuZ1Bvilu8sXly.png"),
			JellyseerrNetworkDto(id = 13, name = "Nickelodeon", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ikZXxg6GnwpzqiZbRPhJGaZapqB.png"),
			JellyseerrNetworkDto(id = 3353, name = "Peacock", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/gIAcGTjKKr0KOHL5s4O36roJ8p7.png"),
		)
		
		// Popular movie studios (from Seerr - using duotone filtered URLs)
		val POPULAR_STUDIOS = listOf(
			JellyseerrStudioDto(id = 2, name = "Disney", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/wdrCwmRnLFJhEoH8GSfymY85KHT.png"),
			JellyseerrStudioDto(id = 127928, name = "20th Century Studios", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/h0rjX5vjW5r8yEnUBStFarjcLT4.png"),
			JellyseerrStudioDto(id = 34, name = "Sony Pictures", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/GagSvqWlyPdkFHMfQ3pNq6ix9P.png"),
			JellyseerrStudioDto(id = 174, name = "Warner Bros. Pictures", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ky0xOc5OrhzkZ1N6KyUxacfQsCk.png"),
			JellyseerrStudioDto(id = 33, name = "Universal", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/8lvHyhjr8oUKOOy2dKXoALWKdp0.png"),
			JellyseerrStudioDto(id = 4, name = "Paramount", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/fycMZt242LVjagMByZOLUGbCvv3.png"),
			JellyseerrStudioDto(id = 3, name = "Pixar", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1TjvGVDMYsj6JBxOAkUHpPEwLf7.png"),
			JellyseerrStudioDto(id = 521, name = "DreamWorks", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/kP7t6RwGz2AvvTkvnI1uteEwHet.png"),
			JellyseerrStudioDto(id = 420, name = "Marvel Studios", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/hUzeosd33nzE5MCNsZxCGEKTXaQ.png"),
			JellyseerrStudioDto(id = 9993, name = "DC", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/2Tc1P3Ac8M479naPp1kYT3izLS5.png"),
			JellyseerrStudioDto(id = 41077, name = "A24", logoPath = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1ZXsGaFPgrgS6ZZGS37AqD5uU12.png"),
		)
	}

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

	private val _movieGenres = MutableStateFlow<List<JellyseerrGenreDto>>(emptyList())
	val movieGenres: StateFlow<List<JellyseerrGenreDto>> = _movieGenres.asStateFlow()

	private val _tvGenres = MutableStateFlow<List<JellyseerrGenreDto>>(emptyList())
	val tvGenres: StateFlow<List<JellyseerrGenreDto>> = _tvGenres.asStateFlow()
	
	private val _networks = MutableStateFlow(POPULAR_NETWORKS)
	val networks: StateFlow<List<JellyseerrNetworkDto>> = _networks.asStateFlow()
	
	private val _studios = MutableStateFlow(POPULAR_STUDIOS)
	val studios: StateFlow<List<JellyseerrStudioDto>> = _studios.asStateFlow()

	private var trendingCurrentPage = 3
	private var trendingMoviesCurrentPage = 3
	private var trendingTvCurrentPage = 3
	private var upcomingMoviesCurrentPage = 3
	private var upcomingTvCurrentPage = 3
	private var isLoadingMoreTrending = false
	private var isLoadingMoreTrendingMovies = false
	private var isLoadingMoreTrendingTv = false
	private var isLoadingMoreUpcomingMovies = false
	private var isLoadingMoreUpcomingTv = false

	private fun List<JellyseerrDiscoverItemDto>.filterNsfw(blockNsfw: Boolean): List<JellyseerrDiscoverItemDto> {
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
	val isMoonfinMode: StateFlow<Boolean> = jellyseerrRepository.isMoonfinMode

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

	/** Returns true if discover content has already been loaded (avoids redundant API calls on resume). */
	fun hasContent(): Boolean {
		return _trending.value.isNotEmpty() ||
			_trendingMovies.value.isNotEmpty() ||
			_trendingTv.value.isNotEmpty() ||
			_upcomingMovies.value.isNotEmpty() ||
			_upcomingTv.value.isNotEmpty()
	}

	fun loadTrendingContent() {
		viewModelScope.launch {
			if (!isAvailable.value) {
				Timber.d("JellyseerrViewModel: Skipping loadTrendingContent - not available")
				return@launch
			}
			_loadingState.emit(JellyseerrLoadingState.Loading)
		try {
			// Get preferences for fetch limit and NSFW filter
			val prefs = getPreferences()
			val itemsPerPage = prefs?.get(JellyseerrPreferences.fetchLimit)?.limit ?: JellyseerrFetchLimit.MEDIUM.limit
			val blockNsfw = prefs?.get(JellyseerrPreferences.blockNsfw) ?: false
			
			var hasPermissionError = false
			
			val results = coroutineScope {
				listOf(
					async { jellyseerrRepository.getTrending(limit = itemsPerPage, offset = 0) },
					async { jellyseerrRepository.getTrendingMovies(limit = itemsPerPage, offset = 0) },
					async { jellyseerrRepository.getTrendingTv(limit = itemsPerPage, offset = 0) },
					async { jellyseerrRepository.getUpcomingMovies(limit = itemsPerPage, offset = 0) },
					async { jellyseerrRepository.getUpcomingTv(limit = itemsPerPage, offset = 0) },
				).awaitAll()
			}
			
			val trendingResult = results[0]
			val trendingMoviesResult = results[1]
			val trendingTvResult = results[2]
			val upcomingMoviesResult = results[3]
			val upcomingTvResult = results[4]
			
			if (trendingResult.isFailure && trendingResult.exceptionOrNull()?.message?.contains("403") == true) {
				hasPermissionError = true
			}

			val allTrending = trendingResult.getOrNull()?.results ?: emptyList()
			val allTrendingMovies = trendingMoviesResult.getOrNull()?.results ?: emptyList()
			val allTrendingTv = trendingTvResult.getOrNull()?.results ?: emptyList()
			val allUpcomingMovies = upcomingMoviesResult.getOrNull()?.results ?: emptyList()
			val allUpcomingTv = upcomingTvResult.getOrNull()?.results ?: emptyList()

			if (allTrending.isNotEmpty() || allTrendingMovies.isNotEmpty() || allTrendingTv.isNotEmpty()) {
				// Filter out already-available content, blacklisted items (server-side status), NSFW content
				val trending = allTrending
					.filterNot { it.isAvailable() }
					.filterNot { it.isBlacklisted() }
					.filter { (it.mediaType ?: "").lowercase() in listOf("movie", "tv") }
					.filterNsfw(blockNsfw)
				val trendingMovies = allTrendingMovies
					.filterNot { it.isAvailable() }
					.filterNot { it.isBlacklisted() }
					.filter { (it.mediaType ?: "").lowercase() == "movie" }
					.filterNsfw(blockNsfw)
				val trendingTv = allTrendingTv
					.filterNot { it.isAvailable() }
					.filterNot { it.isBlacklisted() }
					.filter { (it.mediaType ?: "").lowercase() == "tv" }
					.filterNsfw(blockNsfw)
				val upcomingMovies = allUpcomingMovies
					.filterNot { it.isAvailable() }
					.filterNot { it.isBlacklisted() }
					.filter { (it.mediaType ?: "").lowercase() == "movie" }
					.filterNsfw(blockNsfw)
				val upcomingTv = allUpcomingTv
					.filterNot { it.isAvailable() }
					.filterNot { it.isBlacklisted() }
					.filter { (it.mediaType ?: "").lowercase() == "tv" }
					.filterNsfw(blockNsfw)
				
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
				
				trendingCurrentPage = 1
				trendingMoviesCurrentPage = 1
				trendingTvCurrentPage = 1
				upcomingMoviesCurrentPage = 1
				upcomingTvCurrentPage = 1
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
			val errorMessage = ErrorHandler.handle(error, "load trending content")
			_loadingState.emit(JellyseerrLoadingState.Error(errorMessage))
		}
	}
}

	fun loadRequests() {
		viewModelScope.launch {
			if (!isAvailable.value) {
				Timber.d("JellyseerrViewModel: Skipping loadRequests - not available")
				return@launch
			}
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
				limit = 20
			)
				
				if (result.isSuccess) {
					val userRequests = result.getOrNull()?.results ?: emptyList()
					Timber.d("JellyseerrViewModel: Fetched ${userRequests.size} requests for user ${currentUser.id}")
					
					// Filter by status BEFORE enrichment to avoid wasted API calls
					val filteredRequests = userRequests.filter { request ->
						when (request.status) {
							1 -> true // Pending - always show
							2 -> true // Approved/Processing - always show  
							3 -> isWithinDays(request.updatedAt, 3) // Declined - recent only
							4 -> isWithinDays(request.updatedAt, 3) // Available - recent only
							else -> true
						}
					}
					Timber.d("JellyseerrViewModel: Filtered ${userRequests.size} to ${filteredRequests.size} before enrichment")
					
					// Cache to avoid duplicate fetches for the same TMDB ID
					val movieCache = mutableMapOf<Int, JellyseerrMediaDto?>()
					val tvCache = mutableMapOf<Int, JellyseerrMediaDto?>()
					val semaphore = kotlinx.coroutines.sync.Semaphore(5)
					
					val enrichedRequests = coroutineScope {
						filteredRequests.map { request ->
							async {
								val tmdbId = request.media?.tmdbId
								if (tmdbId == null) {
									Timber.w("JellyseerrViewModel: Request ${request.id} has no tmdbId, skipping enrichment")
									return@async request
								}
								
								val enrichedMedia = when (request.type) {
									"movie" -> {
										movieCache.getOrPut(tmdbId) {
											semaphore.acquire()
											try {
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
													Timber.w("JellyseerrViewModel: Failed to fetch movie details for tmdbId: $tmdbId")
													request.media
												}
											} finally {
												semaphore.release()
											}
										}
									}
									"tv" -> {
										tvCache.getOrPut(tmdbId) {
											semaphore.acquire()
											try {
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
													Timber.w("JellyseerrViewModel: Failed to fetch TV details for tmdbId: $tmdbId")
													request.media
												}
											} finally {
												semaphore.release()
											}
										}
									}
									else -> {
										Timber.w("JellyseerrViewModel: Unknown media type: ${request.type}")
										request.media
									}
								}
								
								request.copy(media = enrichedMedia)
							}
						}.awaitAll()
					}
					
					enrichedRequests.forEach { request ->
						Timber.d("JellyseerrViewModel: Request ${request.id} - Type: ${request.type}, Status: ${request.status}, Media: ${request.media?.title ?: request.media?.name}, RequestedBy: ${request.requestedBy?.username}")
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
}

	fun loadGenres() {
		viewModelScope.launch {
			if (!isAvailable.value) {
				Timber.d("JellyseerrViewModel: Skipping loadGenres - not available")
				return@launch
			}
			try {
				coroutineScope {
					val movieGenresDeferred = async { jellyseerrRepository.getGenreSliderMovies() }
					val tvGenresDeferred = async { jellyseerrRepository.getGenreSliderTv() }
					
					val movieGenresResult = movieGenresDeferred.await()
					val tvGenresResult = tvGenresDeferred.await()
					
					if (movieGenresResult.isSuccess) {
						val genres = movieGenresResult.getOrNull() ?: emptyList()
						_movieGenres.emit(genres)
						Timber.d("JellyseerrViewModel: Loaded ${genres.size} movie genres")
					}
					if (tvGenresResult.isSuccess) {
						val genres = tvGenresResult.getOrNull() ?: emptyList()
						_tvGenres.emit(genres)
						Timber.d("JellyseerrViewModel: Loaded ${genres.size} TV genres")
					}
				}
			} catch (error: Exception) {
				Timber.e(error, "Failed to load genres")
			}
		}
	}

	fun loadNextTrendingPage() {
		if (isLoadingMoreTrending) return
		viewModelScope.launch {
			isLoadingMoreTrending = true
			try {
				val prefs = getPreferences()
				val itemsPerPage = prefs?.get(JellyseerrPreferences.fetchLimit)?.limit ?: JellyseerrFetchLimit.MEDIUM.limit
				val blockNsfw = prefs?.get(JellyseerrPreferences.blockNsfw) ?: false
				trendingCurrentPage++
				val offset = (trendingCurrentPage - 1) * itemsPerPage
				val result = jellyseerrRepository.getTrending(limit = itemsPerPage, offset = offset)
				
				if (result.isSuccess) {
					val newItems = result.getOrNull()?.results ?: emptyList()
					val filtered = newItems
						.filterNot { it.isAvailable() }
						.filterNot { it.isBlacklisted() }
						.filter { (it.mediaType ?: "").lowercase() in listOf("movie", "tv") }
						.filterNsfw(blockNsfw)
					val currentList = _trending.value.toMutableList()
					currentList.addAll(filtered)
					_trending.emit(currentList)
					Timber.d("JellyseerrViewModel: Loaded page $trendingCurrentPage - added ${filtered.size} trending items, total: ${currentList.size}")
				}
			} catch (error: Exception) {
				Timber.e(error, "Failed to load more trending")
			} finally {
				isLoadingMoreTrending = false
			}
		}
	}

	fun loadNextTrendingMoviesPage() {
		if (isLoadingMoreTrendingMovies) return
		viewModelScope.launch {
			isLoadingMoreTrendingMovies = true
			try {
				val prefs = getPreferences()
				val itemsPerPage = prefs?.get(JellyseerrPreferences.fetchLimit)?.limit ?: JellyseerrFetchLimit.MEDIUM.limit
				val blockNsfw = prefs?.get(JellyseerrPreferences.blockNsfw) ?: false
				trendingMoviesCurrentPage++
				val offset = (trendingMoviesCurrentPage - 1) * itemsPerPage
				val result = jellyseerrRepository.getTrendingMovies(limit = itemsPerPage, offset = offset)
				
				if (result.isSuccess) {
					val newItems = result.getOrNull()?.results ?: emptyList()
					val filtered = newItems
						.filterNot { it.isAvailable() }
						.filterNot { it.isBlacklisted() }
						.filter { (it.mediaType ?: "").lowercase() == "movie" }
						.filterNsfw(blockNsfw)
					val currentList = _trendingMovies.value.toMutableList()
					currentList.addAll(filtered)
					_trendingMovies.emit(currentList)
					Timber.d("JellyseerrViewModel: Loaded page $trendingMoviesCurrentPage - added ${filtered.size} movie items, total: ${currentList.size}")
				}
			} catch (error: Exception) {
				Timber.e(error, "Failed to load more trending movies")
			} finally {
				isLoadingMoreTrendingMovies = false
			}
		}
	}

	fun loadNextTrendingTvPage() {
		if (isLoadingMoreTrendingTv) return
		viewModelScope.launch {
			isLoadingMoreTrendingTv = true
			try {
				val prefs = getPreferences()
				val itemsPerPage = prefs?.get(JellyseerrPreferences.fetchLimit)?.limit ?: JellyseerrFetchLimit.MEDIUM.limit
				val blockNsfw = prefs?.get(JellyseerrPreferences.blockNsfw) ?: false
				trendingTvCurrentPage++
				val offset = (trendingTvCurrentPage - 1) * itemsPerPage
				val result = jellyseerrRepository.getTrendingTv(limit = itemsPerPage, offset = offset)
				
				if (result.isSuccess) {
					val newItems = result.getOrNull()?.results ?: emptyList()
					val filtered = newItems
						.filterNot { it.isAvailable() }
						.filterNot { it.isBlacklisted() }
						.filter { (it.mediaType ?: "").lowercase() == "tv" }
						.filterNsfw(blockNsfw)
					val currentList = _trendingTv.value.toMutableList()
					currentList.addAll(filtered)
					_trendingTv.emit(currentList)
					Timber.d("JellyseerrViewModel: Loaded page $trendingTvCurrentPage - added ${filtered.size} TV items, total: ${currentList.size}")
				}
			} catch (error: Exception) {
				Timber.e(error, "Failed to load more trending TV")
			} finally {
				isLoadingMoreTrendingTv = false
			}
		}
	}

	fun loadNextUpcomingMoviesPage() {
		if (isLoadingMoreUpcomingMovies) return
		viewModelScope.launch {
			isLoadingMoreUpcomingMovies = true
			try {
				val prefs = getPreferences()
				val itemsPerPage = prefs?.get(JellyseerrPreferences.fetchLimit)?.limit ?: JellyseerrFetchLimit.MEDIUM.limit
				val blockNsfw = prefs?.get(JellyseerrPreferences.blockNsfw) ?: false
				upcomingMoviesCurrentPage++
				val offset = (upcomingMoviesCurrentPage - 1) * itemsPerPage
				val result = jellyseerrRepository.getUpcomingMovies(limit = itemsPerPage, offset = offset)
				
				if (result.isSuccess) {
					val newItems = result.getOrNull()?.results ?: emptyList()
					val filtered = newItems
						.filterNot { it.isAvailable() }
						.filterNot { it.isBlacklisted() }
						.filter { (it.mediaType ?: "").lowercase() == "movie" }
						.filterNsfw(blockNsfw)
					val currentList = _upcomingMovies.value.toMutableList()
					currentList.addAll(filtered)
					_upcomingMovies.emit(currentList)
					Timber.d("JellyseerrViewModel: Loaded page $upcomingMoviesCurrentPage - added ${filtered.size} upcoming movie items, total: ${currentList.size}")
				}
			} catch (error: Exception) {
				Timber.e(error, "Failed to load more upcoming movies")
			} finally {
				isLoadingMoreUpcomingMovies = false
			}
		}
	}

	fun loadNextUpcomingTvPage() {
		if (isLoadingMoreUpcomingTv) return
		viewModelScope.launch {
			isLoadingMoreUpcomingTv = true
			try {
				val prefs = getPreferences()
				val itemsPerPage = prefs?.get(JellyseerrPreferences.fetchLimit)?.limit ?: JellyseerrFetchLimit.MEDIUM.limit
				val blockNsfw = prefs?.get(JellyseerrPreferences.blockNsfw) ?: false
				upcomingTvCurrentPage++
				val offset = (upcomingTvCurrentPage - 1) * itemsPerPage
				val result = jellyseerrRepository.getUpcomingTv(limit = itemsPerPage, offset = offset)
				
				if (result.isSuccess) {
					val newItems = result.getOrNull()?.results ?: emptyList()
					val filtered = newItems
						.filterNot { it.isAvailable() }
						.filterNot { it.isBlacklisted() }
						.filter { (it.mediaType ?: "").lowercase() == "tv" }
						.filterNsfw(blockNsfw)
					val currentList = _upcomingTv.value.toMutableList()
					currentList.addAll(filtered)
					_upcomingTv.emit(currentList)
					Timber.d("JellyseerrViewModel: Loaded page $upcomingTvCurrentPage - added ${filtered.size} upcoming TV items, total: ${currentList.size}")
				}
			} catch (error: Exception) {
				Timber.e(error, "Failed to load more upcoming TV")
			} finally {
				isLoadingMoreUpcomingTv = false
			}
		}
	}

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

	suspend fun getRecommendationsMovies(tmdbId: Int, page: Int = 1) = jellyseerrRepository.getRecommendationsMovies(tmdbId, page)

	suspend fun getRecommendationsTv(tmdbId: Int, page: Int = 1) = jellyseerrRepository.getRecommendationsTv(tmdbId, page)

	suspend fun getPersonDetails(personId: Int) = jellyseerrRepository.getPersonDetails(personId)

	suspend fun getPersonCombinedCredits(personId: Int) = jellyseerrRepository.getPersonCombinedCredits(personId)

	suspend fun discoverMovies(
		page: Int = 1,
		sortBy: String = "popularity.desc",
		genreId: String? = null,
		studioId: String? = null,
		keywords: String? = null,
		language: String = "en"
	) = jellyseerrRepository.discoverMovies(
		page = page,
		sortBy = sortBy,
		genre = genreId?.toIntOrNull(),
		studio = studioId?.toIntOrNull(),
		keywords = keywords?.toIntOrNull(),
		language = language
	)

	suspend fun discoverTv(
		page: Int = 1,
		sortBy: String = "popularity.desc",
		genreId: String? = null,
		networkId: String? = null,
		keywords: String? = null,
		language: String = "en"
	) = jellyseerrRepository.discoverTv(
		page = page,
		sortBy = sortBy,
		genre = genreId?.toIntOrNull(),
		network = networkId?.toIntOrNull(),
		keywords = keywords?.toIntOrNull(),
		language = language
	)

	private suspend fun requestContent(
		mediaId: Int,
		mediaType: String,
		seasons: List<Int>?,
		is4k: Boolean = false,
		advancedOptions: AdvancedRequestOptions? = null
	) {
		Timber.d("JellyseerrViewModel: Requesting media - ID: $mediaId, Type: $mediaType, Seasons: $seasons, 4K: $is4k")
		
		// Convert seasons list to Seasons sealed class
		val seasonsParam = when {
			mediaType != "tv" -> null
			seasons == null -> Seasons.All
			else -> Seasons.List(seasons)
		}
		
		// Get user preferences for profile IDs
		val prefs = getPreferences()
		
		// Use advanced options if provided, otherwise fall back to preferences
		val profileId = advancedOptions?.profileId ?: when {
			mediaType == "movie" && is4k -> prefs?.get(JellyseerrPreferences.fourKMovieProfileId)?.toIntOrNull()
			mediaType == "movie" && !is4k -> prefs?.get(JellyseerrPreferences.hdMovieProfileId)?.toIntOrNull()
			mediaType == "tv" && is4k -> prefs?.get(JellyseerrPreferences.fourKTvProfileId)?.toIntOrNull()
			mediaType == "tv" && !is4k -> prefs?.get(JellyseerrPreferences.hdTvProfileId)?.toIntOrNull()
			else -> null
		}
		
		val rootFolderId = advancedOptions?.rootFolderId ?: when {
			mediaType == "movie" && is4k -> prefs?.get(JellyseerrPreferences.fourKMovieRootFolderId)?.toIntOrNull()
			mediaType == "movie" && !is4k -> prefs?.get(JellyseerrPreferences.hdMovieRootFolderId)?.toIntOrNull()
			mediaType == "tv" && is4k -> prefs?.get(JellyseerrPreferences.fourKTvRootFolderId)?.toIntOrNull()
			mediaType == "tv" && !is4k -> prefs?.get(JellyseerrPreferences.hdTvRootFolderId)?.toIntOrNull()
			else -> null
		}
		
		val serverId = advancedOptions?.serverId ?: when {
			mediaType == "movie" && is4k -> prefs?.get(JellyseerrPreferences.fourKMovieServerId)?.toIntOrNull()
			mediaType == "movie" && !is4k -> prefs?.get(JellyseerrPreferences.hdMovieServerId)?.toIntOrNull()
			mediaType == "tv" && is4k -> prefs?.get(JellyseerrPreferences.fourKTvServerId)?.toIntOrNull()
			mediaType == "tv" && !is4k -> prefs?.get(JellyseerrPreferences.hdTvServerId)?.toIntOrNull()
			else -> null
		}
		
		Timber.d("JellyseerrViewModel: Using profiles - profileId=$profileId, rootFolderId=$rootFolderId, serverId=$serverId (from advancedOptions: ${advancedOptions != null})")
		
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
		is4k: Boolean = false,
		advancedOptions: AdvancedRequestOptions? = null
	): Result<Unit> {
		return try {
			val mediaType = item.mediaType ?: return Result.failure(Exception("Unknown media type"))
			val mediaId = item.id
			requestContent(mediaId, mediaType, seasons, is4k, advancedOptions)
			Result.success(Unit)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	fun search(query: String, mediaType: String? = null) {
		viewModelScope.launch {
			if (query.isBlank()) {
				_searchResults.emit(emptyList())
				return@launch
			}

			_loadingState.emit(JellyseerrLoadingState.Loading)
			try {
				// Get preferences for fetch limit and NSFW filter
				val prefs = getPreferences()
				val searchLimit = prefs?.get(JellyseerrPreferences.fetchLimit)?.limit ?: JellyseerrFetchLimit.MEDIUM.limit
				val blockNsfw = prefs?.get(JellyseerrPreferences.blockNsfw) ?: false
				
				val result = jellyseerrRepository.search(query, mediaType, limit = searchLimit)
			if (result.isSuccess) {
				val results = result.getOrNull()?.results ?: emptyList()
				Timber.d("Jellyseerr Search: Raw results count: ${results.size}")
				
				// Filter out already-available content, blacklisted items (server-side status), and NSFW content
				val filteredResults = results
					.filterNot { it.isAvailable() }
					.filterNot { it.isBlacklisted() }
					.filterNsfw(blockNsfw)
				
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

	suspend fun getCurrentUser() = jellyseerrRepository.getCurrentUser()

	suspend fun getRadarrSettings() = jellyseerrRepository.getRadarrSettings()

	suspend fun getSonarrSettings() = jellyseerrRepository.getSonarrSettings()

	suspend fun getRadarrServers() = jellyseerrRepository.getRadarrServers()

	suspend fun getRadarrServerDetails(serverId: Int) = jellyseerrRepository.getRadarrServerDetails(serverId)

	suspend fun getSonarrServers() = jellyseerrRepository.getSonarrServers()

	suspend fun getSonarrServerDetails(serverId: Int) = jellyseerrRepository.getSonarrServerDetails(serverId)

	suspend fun cancelRequest(requestId: Int): Result<Unit> {
		Timber.d("JellyseerrViewModel: Cancelling request ID: $requestId")
		val result = jellyseerrRepository.deleteRequest(requestId)
		if (result.isSuccess) {
			Timber.d("JellyseerrViewModel: Request $requestId cancelled successfully")
			loadRequestsSuspend()
		} else {
			Timber.e(result.exceptionOrNull(), "JellyseerrViewModel: Failed to cancel request $requestId")
		}
		return result
	}

	override fun onCleared() {
		super.onCleared()
		jellyseerrRepository.close()
	}
}
