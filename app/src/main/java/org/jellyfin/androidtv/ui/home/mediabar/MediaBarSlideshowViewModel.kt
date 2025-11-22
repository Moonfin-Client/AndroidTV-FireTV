package org.jellyfin.androidtv.ui.home.mediabar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
) : ViewModel() {
	private val config = MediaBarConfig()

	private val _state = MutableStateFlow<MediaBarState>(MediaBarState.Loading)
	val state: StateFlow<MediaBarState> = _state.asStateFlow()

	private val _playbackState = MutableStateFlow(SlideshowPlaybackState())
	val playbackState: StateFlow<SlideshowPlaybackState> = _playbackState.asStateFlow()

	private val _isFocused = MutableStateFlow(false)
	val isFocused: StateFlow<Boolean> = _isFocused.asStateFlow()

	private var items: List<MediaBarSlideItem> = emptyList()
	private var autoAdvanceJob: Job? = null

	init {
		loadSlideshowItems()
	}

	fun setFocused(focused: Boolean) {
		_isFocused.value = focused
	}

	/**
	 * Load featured media items for the slideshow
	 */
	private fun loadSlideshowItems() {
		viewModelScope.launch {
			try {
				_state.value = MediaBarState.Loading

				// Fetch movies and shows randomly on IO dispatcher
				val (moviesResponse, showsResponse) = withContext(Dispatchers.IO) {
					val movies by api.itemsApi.getItems(
						includeItemTypes = setOf(BaseItemKind.MOVIE),
						recursive = true,
						sortBy = setOf(org.jellyfin.sdk.model.api.ItemSortBy.RANDOM),
						limit = config.maxItems * 2, // Get more items to filter from
						filters = setOf(ItemFilter.IS_NOT_FOLDER),
						fields = setOf(
							ItemFields.OVERVIEW,
							ItemFields.GENRES,
						),
						imageTypeLimit = 1,
						enableImageTypes = setOf(ImageType.BACKDROP, ImageType.LOGO),
					)

					val shows by api.itemsApi.getItems(
						includeItemTypes = setOf(BaseItemKind.SERIES),
						recursive = true,
						sortBy = setOf(org.jellyfin.sdk.model.api.ItemSortBy.RANDOM),
						limit = config.maxItems * 2, // Get more items to filter from
						filters = setOf(ItemFilter.IS_NOT_FOLDER),
						fields = setOf(
							ItemFields.OVERVIEW,
							ItemFields.GENRES,
						),
						imageTypeLimit = 1,
						enableImageTypes = setOf(ImageType.BACKDROP, ImageType.LOGO),
					)
					
					Pair(movies, shows)
				}

				val allItems = (moviesResponse.items.orEmpty() + showsResponse.items.orEmpty())
					.filter { it.backdropImageTags?.isNotEmpty() == true }
					.shuffled()
					.take(config.maxItems)

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
						runtime = item.runTimeTicks?.let { ticks ->
							val minutes = (ticks / 600000000)
							"${minutes}m"
						},
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
		autoAdvanceJob = viewModelScope.launch {
			delay(config.shuffleIntervalMs)
			if (!_playbackState.value.isPaused && !_playbackState.value.isTransitioning) {
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
