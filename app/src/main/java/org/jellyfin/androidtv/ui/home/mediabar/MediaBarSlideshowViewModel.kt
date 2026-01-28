package org.jellyfin.androidtv.ui.home.mediabar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.data.repository.MultiServerRepository
import org.jellyfin.androidtv.data.repository.ParentalControlsRepository
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.sdk.api.client.ApiClient
import android.content.Context
import coil3.ImageLoader
import coil3.request.ImageRequest
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import timber.log.Timber
import java.util.Locale
import java.util.UUID

class MediaBarSlideshowViewModel(
	private val api: ApiClient,
	private val userSettingPreferences: UserSettingPreferences,
	private val itemMutationRepository: ItemMutationRepository,
	private val userRepository: UserRepository,
	private val context: Context,
	private val imageLoader: ImageLoader,
	private val multiServerRepository: MultiServerRepository,
	private val parentalControlsRepository: ParentalControlsRepository,
) : ViewModel() {
	private fun getConfig() = MediaBarConfig(
		maxItems = userSettingPreferences[UserSettingPreferences.mediaBarItemCount].toIntOrNull() ?: 10
	)

	private val _state = MutableStateFlow<MediaBarState>(MediaBarState.Loading)
	val state: StateFlow<MediaBarState> = _state.asStateFlow()

	private val _playbackState = MutableStateFlow(SlideshowPlaybackState())
	val playbackState: StateFlow<SlideshowPlaybackState> = _playbackState.asStateFlow()

	private val _isFocused = MutableStateFlow(false)
	val isFocused: StateFlow<Boolean> = _isFocused.asStateFlow()

	private var items: List<MediaBarSlideItem> = emptyList()
	private var autoAdvanceJob: Job? = null
	private var currentUserId: UUID? = null

	init {
		// Observe user changes and reload content when user switches
		userRepository.currentUser
			.filterNotNull()
			.onEach { user ->
				// Check if user has actually changed (not just initial load)
				if (currentUserId != null && currentUserId != user.id) {
					reloadContent()
				}
				currentUserId = user.id
			}
			.launchIn(viewModelScope)
	}

	fun setFocused(focused: Boolean) {
		_isFocused.value = focused

		// When losing focus, stop auto-advance
		if (!focused) {
			autoAdvanceJob?.cancel()
		} else {
			// When gaining focus, refresh non-visible items for variety
			// but keep the current and adjacent items to prevent flickering
			if (items.isNotEmpty()) {
				refreshBackgroundItems()
			}
			
			// Restart auto-advance if not paused
			if (!_playbackState.value.isPaused) {
				resetAutoAdvanceTimer()
			}
		}
	}

	/**
	 * Fetch items of a specific type from a server.
	 * Helper function to avoid code duplication.
	 * 
	 * Note: TV series are folders (they contain episodes), so we exclude the
	 * IS_NOT_FOLDER filter for SERIES to properly fetch shows.
	 * 
	 * @param apiClient The API client to use for this request (supports multi-server)
	 * @param userId The user ID for authentication on this specific server
	 * @param itemType The type of item to fetch (MOVIE or SERIES)
	 * @param maxItems Maximum number of items to fetch
	 */
	private suspend fun fetchItemsFromServer(
		apiClient: ApiClient,
		userId: UUID,
		itemType: BaseItemKind,
		maxItems: Int
	): List<BaseItemDto> {
		// Only apply IS_NOT_FOLDER filter for movies since Series are folders
		val filters = if (itemType == BaseItemKind.SERIES) {
			emptySet()
		} else {
			setOf(ItemFilter.IS_NOT_FOLDER)
		}
		
		// Get parent library IDs that match the requested item type
		// This prevents scanning through unrelated libraries (e.g., music, recordings, live TV)
		val matchingLibraries = try {
			val viewsResponse by apiClient.itemsApi.getItems(
				includeItemTypes = setOf(org.jellyfin.sdk.model.api.BaseItemKind.COLLECTION_FOLDER),
				userId = userId,
			)
			viewsResponse.items.orEmpty()
				.filter { view ->
					// ONLY include movie or TV show libraries based on what we're fetching
					// Excludes: music, recordings, live TV, photos, books, etc.
					val collectionType = view.collectionType?.toString()?.lowercase(Locale.ROOT)
					when (itemType) {
						BaseItemKind.MOVIE -> collectionType == "movies"
						BaseItemKind.SERIES -> collectionType == "tvshows"
						else -> false
					}
				}
		} catch (e: Exception) {
			Timber.w(e, "Failed to get library views")
			emptyList()
		}
		
		// If no matching libraries found, return empty list immediately
		// This prevents slow recursive searches through all libraries
		if (matchingLibraries.isEmpty()) {
			Timber.d("MediaBar: No ${itemType.name} libraries found, skipping fetch")
			return emptyList()
		}
		
		// Fetch from ALL matching libraries and combine results
		// Distribute the item count across libraries for better variety
		val itemsPerLibrary = (maxItems * 1.5 / matchingLibraries.size).toInt().coerceAtLeast(5)
		
		return matchingLibraries.mapNotNull { library ->
			try {
				val response by apiClient.itemsApi.getItems(
					includeItemTypes = setOf(itemType),
					parentId = library.id,
					recursive = true,
					sortBy = setOf(org.jellyfin.sdk.model.api.ItemSortBy.RANDOM),
					limit = itemsPerLibrary,
					filters = filters,
					fields = setOf(ItemFields.OVERVIEW, ItemFields.GENRES, ItemFields.PROVIDER_IDS),
					imageTypeLimit = 1,
					enableImageTypes = setOf(ImageType.BACKDROP, ImageType.LOGO),
				)
				response.items.orEmpty()
			} catch (e: Exception) {
				Timber.w(e, "Failed to fetch from library ${library.name}")
				null
			}
		}.flatten()
	}

	/**
	 * Data class to hold item with its associated API client for URL generation.
	 */
	private data class ItemWithApiClient(
		val item: BaseItemDto,
		val apiClient: ApiClient,
		val serverId: UUID? = null
	)

	/**
	 * Load featured media items for the slideshow.
	 * Uses double-randomization strategy:
	 * 1. Server-side: sortBy RANDOM returns random set from server
	 * 2. Client-side: shuffle() randomizes the combined results again
	 *
	 * Optimized to fetch movies and shows in parallel for faster loading.
	 * Respects user's content type preference (movies/tv/both).
	 * 
	 * Multi-server support:
	 * - If more than one server is logged in, fetches from all servers
	 * - If only one server, uses current behavior (default API client)
	 */
	private fun loadSlideshowItems() {
		viewModelScope.launch {
		try {
			_state.value = MediaBarState.Loading
			val config = getConfig()
			val contentType = userSettingPreferences[UserSettingPreferences.mediaBarContentType]

			// Get logged in servers
			val loggedInServers = withContext(Dispatchers.IO) {
				multiServerRepository.getLoggedInServers()
			}
			
			val useMultiServer = loggedInServers.size > 1
			Timber.d("MediaBar: Loading items from ${loggedInServers.size} server(s), multi-server mode: $useMultiServer")
			// Get current user ID for single-server mode
			val currentUserId = userRepository.currentUser.value?.id
			// Fetch items based on user preference
			val allItemsWithApiClients: List<ItemWithApiClient> = withContext(Dispatchers.IO) {
				if (useMultiServer) {
					// Multi-server: fetch from all servers in parallel
					val itemsPerServer = (config.maxItems / loggedInServers.size).coerceAtLeast(3)
					
					loggedInServers.map { session ->
						async {
							// Add 10 second timeout per server to prevent slow servers from blocking
							withTimeoutOrNull(10_000L) {
								try {
									val serverItems = when (contentType) {
										"movies" -> fetchItemsFromServer(session.apiClient, session.userId, BaseItemKind.MOVIE, itemsPerServer)
										"tv" -> fetchItemsFromServer(session.apiClient, session.userId, BaseItemKind.SERIES, itemsPerServer)
										else -> { // "both"
											val movies = async { fetchItemsFromServer(session.apiClient, session.userId, BaseItemKind.MOVIE, itemsPerServer / 2 + 1) }
											val shows = async { fetchItemsFromServer(session.apiClient, session.userId, BaseItemKind.SERIES, itemsPerServer / 2 + 1) }
											movies.await() + shows.await()
										}
									}
									Timber.d("MediaBar: Got ${serverItems.size} items from server ${session.server.name}")
									serverItems.map { ItemWithApiClient(it, session.apiClient, session.server.id) }
								} catch (e: Exception) {
									Timber.e(e, "MediaBar: Failed to fetch from server ${session.server.name}")
									emptyList()
								}
							} ?: run {
								Timber.w("MediaBar: Timeout fetching from server ${session.server.name}")
								emptyList()
							}
						}
					}.awaitAll().flatten()
				} else {
					// Single server: use default API client
					if (currentUserId == null) {
						Timber.w("MediaBar: No current user ID, cannot fetch items")
						emptyList()
					} else {
						val serverItems = when (contentType) {
							"movies" -> fetchItemsFromServer(api, currentUserId, BaseItemKind.MOVIE, config.maxItems)
							"tv" -> fetchItemsFromServer(api, currentUserId, BaseItemKind.SERIES, config.maxItems)
							else -> { // "both"
								val movies = async { fetchItemsFromServer(api, currentUserId, BaseItemKind.MOVIE, config.maxItems) }
								val shows = async { fetchItemsFromServer(api, currentUserId, BaseItemKind.SERIES, config.maxItems) }
								movies.await() + shows.await()
							}
						}
						serverItems.map { ItemWithApiClient(it, api) }
					}
				}
			}
				.filter { it.item.backdropImageTags?.isNotEmpty() == true }
				// Apply parental controls filtering
				.also { beforeFilter ->
					val blockedCount = beforeFilter.count { parentalControlsRepository.shouldFilterItem(it.item) }
					Timber.d("MediaBar: Before filter: ${beforeFilter.size} items, $blockedCount would be blocked")
				}
				.filter { !parentalControlsRepository.shouldFilterItem(it.item) }
				.also { afterFilter ->
					Timber.d("MediaBar: After filter: ${afterFilter.size} items")
				}
				.shuffled()
				.take(config.maxItems)

			items = allItemsWithApiClients.map { (item, itemApiClient, serverId) ->
				MediaBarSlideItem(
					itemId = item.id,
					serverId = serverId,
					title = item.name.orEmpty(),
					overview = item.overview,
					backdropUrl = item.backdropImageTags?.firstOrNull()?.let { tag ->
						itemApiClient.imageApi.getItemImageUrl(
							itemId = item.id,
							imageType = ImageType.BACKDROP,
							tag = tag,
							maxWidth = 1920,
							quality = 90
						)
					},
					logoUrl = item.imageTags?.get(ImageType.LOGO)?.let { tag ->
						itemApiClient.imageApi.getItemImageUrl(
							itemId = item.id,
							imageType = ImageType.LOGO,
							tag = tag,
							maxWidth = 800,
						)
					},
					rating = item.officialRating,
					year = item.productionYear,
					genres = item.genres.orEmpty().take(3),
					runtime = item.runTimeTicks?.let { ticks -> (ticks / 10000) },
					criticRating = item.criticRating?.toInt(),
					communityRating = item.communityRating,
					tmdbId = item.providerIds?.get("Tmdb"),
					imdbId = item.providerIds?.get("Imdb"),
				)
			}

			if (items.isNotEmpty()) {
					_state.value = MediaBarState.Ready(items)
					// Preload images for initial slide and adjacent ones
					preloadAdjacentImages(0)
					startAutoPlay()
				} else {
					_state.value = MediaBarState.Error("No items found")
				}
			} catch (e: Exception) {
				Timber.e(e, "Failed to load slideshow items: ${e::class.simpleName} - ${e.message}")
				_state.value = MediaBarState.Error("Failed to load items: ${e::class.simpleName ?: "Unknown error"}")
			}
		}
	}

	/**
	 * Start automatic slideshow playback
	 */
	private fun startAutoPlay() {
		autoAdvanceJob?.cancel()

		// Only start auto-play if the media bar is focused
		if (!_isFocused.value) return

		val config = getConfig()
		autoAdvanceJob = viewModelScope.launch {
			delay(config.shuffleIntervalMs)
			if (!_playbackState.value.isPaused && !_playbackState.value.isTransitioning && _isFocused.value) {
				nextSlide()
			}
		}
	}

	/**
	 * Reset the auto-advance timer
	 */
	private fun resetAutoAdvanceTimer() {
		startAutoPlay()
	}

	/**
	 * Reload slideshow content with fresh random items.
	 * Called when:
	 * - User switches profiles
	 * - Media bar gains focus/visibility
	 * - Manual refresh requested
	 */
	fun reloadContent() {
		// Cancel auto-advance
		autoAdvanceJob?.cancel()
		_playbackState.value = SlideshowPlaybackState()
		loadSlideshowItems()
	}

	/**
	 * Load content on HomeFragment creation
	 * Always fetches fresh random items - no caching
	 */
	fun loadInitialContent() {
		loadSlideshowItems()
	}

	/**
	 * Navigate to the next slide
	 */
	fun nextSlide() {
		if (_playbackState.value.isTransitioning) return
		if (items.isEmpty()) return

		val currentIndex = _playbackState.value.currentIndex
		val nextIndex = (currentIndex + 1) % items.size

		_playbackState.value = _playbackState.value.copy(
			currentIndex = nextIndex,
			isTransitioning = true
		)

		val config = getConfig()
		viewModelScope.launch {
			delay(config.fadeTransitionDurationMs)
			_playbackState.value = _playbackState.value.copy(isTransitioning = false)
			// Preload adjacent images after transition completes
			preloadAdjacentImages(nextIndex)
			// Reset the auto-advance timer after manual or automatic navigation
			resetAutoAdvanceTimer()
		}
	}

	/**
	 * Navigate to the previous slide
	 */
	fun previousSlide() {
		if (_playbackState.value.isTransitioning) return
		if (items.isEmpty()) return

		val currentIndex = _playbackState.value.currentIndex
		val previousIndex = if (currentIndex == 0) items.size - 1 else currentIndex - 1

		_playbackState.value = _playbackState.value.copy(
			currentIndex = previousIndex,
			isTransitioning = true
		)

		val config = getConfig()
		viewModelScope.launch {
			delay(config.fadeTransitionDurationMs)
			_playbackState.value = _playbackState.value.copy(isTransitioning = false)
			// Preload adjacent images after transition completes
			preloadAdjacentImages(previousIndex)
			// Reset the auto-advance timer after manual navigation
			resetAutoAdvanceTimer()
		}
	}

	/**
	 * Preload images for slides adjacent to the current one.
	 * This prevents flickering when navigating between slides by ensuring
	 * images are already cached before they're displayed.
	 * 
	 * @param currentIndex The index of the currently displayed slide
	 */
	private fun preloadAdjacentImages(currentIndex: Int) {
		if (items.isEmpty()) return
		
		viewModelScope.launch(Dispatchers.IO) {
			val indicesToPreload = mutableSetOf<Int>()
			
			// Preload current slide first (highest priority)
			indicesToPreload.add(currentIndex)
			
			// Preload next slide
			val nextIndex = (currentIndex + 1) % items.size
			indicesToPreload.add(nextIndex)
			
			// Preload previous slide
			val previousIndex = if (currentIndex == 0) items.size - 1 else currentIndex - 1
			indicesToPreload.add(previousIndex)
			
			// Optionally preload one more slide ahead for smoother auto-advance
			val nextNextIndex = (nextIndex + 1) % items.size
			indicesToPreload.add(nextNextIndex)
			
			// Preload all the images in parallel
			indicesToPreload.forEach { index ->
				val item = items.getOrNull(index) ?: return@forEach
				
				// Preload backdrop
				item.backdropUrl?.let { url ->
					try {
						val request = ImageRequest.Builder(context)
							.data(url)
							.build()
						imageLoader.enqueue(request)
					} catch (e: Exception) {
						Timber.d("Failed to preload backdrop for item ${item.title}: ${e.message}")
					}
				}
				
				// Preload logo
				item.logoUrl?.let { url ->
					try {
						val request = ImageRequest.Builder(context)
							.data(url)
							.build()
						imageLoader.enqueue(request)
					} catch (e: Exception) {
						Timber.d("Failed to preload logo for item ${item.title}: ${e.message}")
					}
				}
			}
		}
	}

	/**
	 * Refresh background items (not currently visible or adjacent) with new random selections.
	 * This provides variety when regaining focus without causing flickering on the current slide.
	 * Keeps the current item and its adjacent items (previous and next) unchanged.
	 * 
	 * Multi-server support: fetches from all servers when multiple are logged in.
	 */
	private fun refreshBackgroundItems() {
		if (items.isEmpty()) return
		
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val currentIndex = _playbackState.value.currentIndex
				val config = getConfig()
				val contentType = userSettingPreferences[UserSettingPreferences.mediaBarContentType]
				
				// Calculate which indices to keep (current, previous, next)
				val indicesToKeep = mutableSetOf<Int>()
				indicesToKeep.add(currentIndex)
				indicesToKeep.add((currentIndex + 1) % items.size)
				indicesToKeep.add(if (currentIndex == 0) items.size - 1 else currentIndex - 1)
				
				// Only refresh if we have more than 3 items (otherwise all are adjacent)
				if (items.size <= 3) return@launch
				
				// Calculate how many new items we need to fetch
				val itemsToReplace = items.size - indicesToKeep.size
				
				// Get logged in servers
				val loggedInServers = multiServerRepository.getLoggedInServers()
				val useMultiServer = loggedInServers.size > 1
				
				// Get current user ID for single-server mode
				val currentUserId = userRepository.currentUser.value?.id
				
				// Fetch new random items (with multi-server support)
				val newItemsWithApiClients: List<ItemWithApiClient> = if (useMultiServer) {
					val itemsPerServer = (itemsToReplace / loggedInServers.size).coerceAtLeast(2)
					loggedInServers.map { session ->
						async {
							// Add 10 second timeout per server to prevent slow servers from blocking
							withTimeoutOrNull(10_000L) {
								try {
									val serverItems = when (contentType) {
										"movies" -> fetchItemsFromServer(session.apiClient, session.userId, BaseItemKind.MOVIE, itemsPerServer)
										"tv" -> fetchItemsFromServer(session.apiClient, session.userId, BaseItemKind.SERIES, itemsPerServer)
										else -> {
											val movies = async { fetchItemsFromServer(session.apiClient, session.userId, BaseItemKind.MOVIE, itemsPerServer / 2 + 1) }
											val shows = async { fetchItemsFromServer(session.apiClient, session.userId, BaseItemKind.SERIES, itemsPerServer / 2 + 1) }
											movies.await() + shows.await()
										}
									}
									serverItems.map { ItemWithApiClient(it, session.apiClient, session.server.id) }
								} catch (e: Exception) {
									Timber.e(e, "MediaBar refresh: Failed to fetch from server ${session.server.name}")
									emptyList()
								}
							} ?: run {
								Timber.w("MediaBar refresh: Timeout fetching from server ${session.server.name}")
								emptyList()
							}
						}
					}.awaitAll().flatten()
				} else {
					if (currentUserId == null) {
						Timber.w("MediaBar refresh: No current user ID, cannot fetch items")
						emptyList()
					} else {
						val serverItems = when (contentType) {
							"movies" -> fetchItemsFromServer(api, currentUserId, BaseItemKind.MOVIE, itemsToReplace)
							"tv" -> fetchItemsFromServer(api, currentUserId, BaseItemKind.SERIES, itemsToReplace)
							else -> {
								val movies = async { fetchItemsFromServer(api, currentUserId, BaseItemKind.MOVIE, itemsToReplace / 2 + 1) }
								val shows = async { fetchItemsFromServer(api, currentUserId, BaseItemKind.SERIES, itemsToReplace / 2 + 1) }
								movies.await() + shows.await()
							}
						}
						serverItems.map { ItemWithApiClient(it, api) }
					}
				}
					.filter { it.item.backdropImageTags?.isNotEmpty() == true }
					.shuffled()
					.take(itemsToReplace)
				
				// Convert to MediaBarSlideItem
				val newSlideItems = newItemsWithApiClients.map { (item, itemApiClient, serverId) ->
					MediaBarSlideItem(
						itemId = item.id,
						serverId = serverId,
						title = item.name.orEmpty(),
						overview = item.overview,
						backdropUrl = item.backdropImageTags?.firstOrNull()?.let { tag ->
							itemApiClient.imageApi.getItemImageUrl(
								itemId = item.id,
								imageType = ImageType.BACKDROP,
								tag = tag,
								maxWidth = 1920,
								quality = 90
							)
						},
						logoUrl = item.imageTags?.get(ImageType.LOGO)?.let { tag ->
							itemApiClient.imageApi.getItemImageUrl(
								itemId = item.id,
								imageType = ImageType.LOGO,
								tag = tag,
								maxWidth = 800,
							)
						},
						rating = item.officialRating,
						year = item.productionYear,
						genres = item.genres.orEmpty().take(3),
						runtime = item.runTimeTicks?.let { ticks -> (ticks / 10000) },
						criticRating = item.criticRating?.toInt(),
						communityRating = item.communityRating,
						tmdbId = item.providerIds?.get("Tmdb"),
						imdbId = item.providerIds?.get("Imdb"),
					)
				}
				
				// Build new items list: keep existing items at protected indices, replace others
				val updatedItems = items.toMutableList()
				var newItemIndex = 0
				
				for (i in items.indices) {
					if (!indicesToKeep.contains(i) && newItemIndex < newSlideItems.size) {
						updatedItems[i] = newSlideItems[newItemIndex]
						newItemIndex++
					}
				}
				
				// Update items list
				items = updatedItems
				_state.value = MediaBarState.Ready(items)
				
				// Preload the newly added items in the background
				withContext(Dispatchers.IO) {
					updatedItems.forEachIndexed { index, item ->
						if (!indicesToKeep.contains(index)) {
							// Preload backdrop
							item.backdropUrl?.let { url ->
								try {
									val request = ImageRequest.Builder(context)
										.data(url)
										.build()
									imageLoader.enqueue(request)
								} catch (e: Exception) {
									Timber.d("Failed to preload backdrop for refreshed item ${item.title}: ${e.message}")
								}
							}
							
							// Preload logo
							item.logoUrl?.let { url ->
								try {
									val request = ImageRequest.Builder(context)
										.data(url)
										.build()
									imageLoader.enqueue(request)
								} catch (e: Exception) {
									Timber.d("Failed to preload logo for refreshed item ${item.title}: ${e.message}")
								}
							}
						}
					}
				}
				
				Timber.d("Refreshed ${newItemIndex} background items while keeping ${indicesToKeep.size} adjacent items")
			} catch (e: Exception) {
				Timber.e(e, "Failed to refresh background items: ${e.message}")
			}
		}
	}

	/**
	 * Toggle pause/play state
	 */
	fun togglePause() {
		_playbackState.value = _playbackState.value.copy(
			isPaused = !_playbackState.value.isPaused
		)
	}
}
