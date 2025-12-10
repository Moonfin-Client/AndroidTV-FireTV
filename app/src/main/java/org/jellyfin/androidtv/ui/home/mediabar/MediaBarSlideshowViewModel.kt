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
			// When gaining focus, just restart auto-advance if not paused
			// Don't reload content - keep the current carousel items
			if (!_playbackState.value.isPaused) {
				resetAutoAdvanceTimer()
			}
		}
	}

	/**
	 * Fetch items of a specific type from the server.
	 * Helper function to avoid code duplication.
	 */
	private suspend fun fetchItems(itemType: BaseItemKind, maxItems: Int): org.jellyfin.sdk.model.api.BaseItemDtoQueryResult {
		val response by api.itemsApi.getItems(
			includeItemTypes = setOf(itemType),
			recursive = true,
			sortBy = setOf(org.jellyfin.sdk.model.api.ItemSortBy.RANDOM),
			limit = (maxItems * 1.5).toInt(), // Fetch 1.5x for filtering
			filters = setOf(ItemFilter.IS_NOT_FOLDER),
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
	 */
	private fun loadSlideshowItems() {
		viewModelScope.launch {
		try {
			_state.value = MediaBarState.Loading
			val config = getConfig()

			// Fetch movies and shows in parallel for better performance
			val allItems: List<org.jellyfin.sdk.model.api.BaseItemDto> = withContext(Dispatchers.IO) {
				val movies = async { fetchItems(BaseItemKind.MOVIE, config.maxItems) }
				val shows = async { fetchItems(BaseItemKind.SERIES, config.maxItems) }

				(movies.await().items.orEmpty() + shows.await().items.orEmpty())
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
			// Reset the auto-advance timer after manual or automatic navigation
			resetAutoAdvanceTimer()
		}
	}

	/**
	 * Navigate to the previous slide
	 */
	fun previousSlide() {
		if (_playbackState.value.isTransitioning) return

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
			// Reset the auto-advance timer after manual navigation
			resetAutoAdvanceTimer()
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
