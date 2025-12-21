package org.jellyfin.androidtv.ui.home.mediabar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.sdk.api.client.ApiClient
import android.content.Context
import coil3.ImageLoader
import coil3.request.ImageRequest
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import timber.log.Timber
import java.util.UUID

class MediaBarSlideshowViewModel(
	private val api: ApiClient,
	private val userSettingPreferences: UserSettingPreferences,
	private val itemMutationRepository: ItemMutationRepository,
	private val userRepository: UserRepository,
	private val context: Context,
	private val imageLoader: ImageLoader,
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
	 * Fetch items of a specific type from the server.
	 * Helper function to avoid code duplication.
	 * 
	 * Note: TV series are folders (they contain episodes), so we exclude the
	 * IS_NOT_FOLDER filter for SERIES to properly fetch shows.
	 */
	private suspend fun fetchItems(itemType: BaseItemKind, maxItems: Int): org.jellyfin.sdk.model.api.BaseItemDtoQueryResult {
		// Only apply IS_NOT_FOLDER filter for movies since Series are folders
		val filters = if (itemType == BaseItemKind.SERIES) {
			emptySet()
		} else {
			setOf(ItemFilter.IS_NOT_FOLDER)
		}
		
		val response by api.itemsApi.getItems(
			includeItemTypes = setOf(itemType),
			recursive = true,
			sortBy = setOf(org.jellyfin.sdk.model.api.ItemSortBy.RANDOM),
			limit = (maxItems * 1.5).toInt(), // Fetch 1.5x for filtering
			filters = filters,
			fields = setOf(ItemFields.OVERVIEW, ItemFields.GENRES),
			imageTypeLimit = 1,
			enableImageTypes = setOf(ImageType.BACKDROP, ImageType.LOGO),
		)
		return response
	}

	/**
	 * Load featured media items for the slideshow.
	 * Uses double-randomization strategy:
	 * 1. Server-side: sortBy RANDOM returns random set from server
	 * 2. Client-side: shuffle() randomizes the combined results again
	 *
	 * Optimized to fetch movies and shows in parallel for faster loading.
	 * Respects user's content type preference (movies/tv/both).
	 */
	private fun loadSlideshowItems() {
		viewModelScope.launch {
		try {
			_state.value = MediaBarState.Loading
			val config = getConfig()
			val contentType = userSettingPreferences[UserSettingPreferences.mediaBarContentType]

			// Fetch items based on user preference
			val allItems: List<org.jellyfin.sdk.model.api.BaseItemDto> = withContext(Dispatchers.IO) {
				when (contentType) {
					"movies" -> {
						fetchItems(BaseItemKind.MOVIE, config.maxItems).items.orEmpty()
					}
					"tv" -> {
						fetchItems(BaseItemKind.SERIES, config.maxItems).items.orEmpty()
					}
					else -> { // "both"
						val movies = async { fetchItems(BaseItemKind.MOVIE, config.maxItems) }
						val shows = async { fetchItems(BaseItemKind.SERIES, config.maxItems) }
						(movies.await().items.orEmpty() + shows.await().items.orEmpty())
					}
				}
					.filter { it.backdropImageTags?.isNotEmpty() == true }
					.shuffled()
					.take(config.maxItems)
			}

			items = allItems.map { item ->
				MediaBarSlideItem(
					itemId = item.id,
					title = item.name.orEmpty(),
					overview = item.overview,
					backdropUrl = item.backdropImageTags?.firstOrNull()?.let { tag ->
						api.imageApi.getItemImageUrl(
							itemId = item.id,
							imageType = ImageType.BACKDROP,
							tag = tag,
							maxWidth = 1920,
							quality = 90
						)
					},
					logoUrl = item.imageTags?.get(ImageType.LOGO)?.let { tag ->
						api.imageApi.getItemImageUrl(
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
				
				// Fetch new random items
				val newItems: List<org.jellyfin.sdk.model.api.BaseItemDto> = when (contentType) {
					"movies" -> {
						fetchItems(BaseItemKind.MOVIE, itemsToReplace).items.orEmpty()
					}
					"tv" -> {
						fetchItems(BaseItemKind.SERIES, itemsToReplace).items.orEmpty()
					}
					else -> { // "both"
						val movies = async { fetchItems(BaseItemKind.MOVIE, itemsToReplace / 2 + 1) }
						val shows = async { fetchItems(BaseItemKind.SERIES, itemsToReplace / 2 + 1) }
						(movies.await().items.orEmpty() + shows.await().items.orEmpty())
					}
				}
					.filter { it.backdropImageTags?.isNotEmpty() == true }
					.shuffled()
					.take(itemsToReplace)
				
				// Convert to MediaBarSlideItem
				val newSlideItems = newItems.map { item ->
					MediaBarSlideItem(
						itemId = item.id,
						title = item.name.orEmpty(),
						overview = item.overview,
						backdropUrl = item.backdropImageTags?.firstOrNull()?.let { tag ->
							api.imageApi.getItemImageUrl(
								itemId = item.id,
								imageType = ImageType.BACKDROP,
								tag = tag,
								maxWidth = 1920,
								quality = 90
							)
						},
						logoUrl = item.imageTags?.get(ImageType.LOGO)?.let { tag ->
							api.imageApi.getItemImageUrl(
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
