package org.jellyfin.androidtv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.JellyseerrRepository
import org.jellyfin.androidtv.data.repository.ParentalControlsRepository
import org.jellyfin.androidtv.data.service.jellyseerr.toBaseItemDto
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.util.ErrorHandler
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SearchViewModel(
	private val searchRepository: SearchRepository,
	private val jellyseerrRepository: JellyseerrRepository,
	private val jellyseerrPreferences: JellyseerrPreferences,
	private val parentalControlsRepository: ParentalControlsRepository,
	private val userPreferences: UserPreferences,
) : ViewModel() {
	companion object {
		private val debounceDuration = 600.milliseconds

		private val groups = mapOf(
			R.string.lbl_movies to setOf(BaseItemKind.MOVIE),
			R.string.lbl_series to setOf(BaseItemKind.SERIES),
			R.string.lbl_episodes to setOf(BaseItemKind.EPISODE),
			R.string.lbl_videos to setOf(BaseItemKind.VIDEO),
			R.string.lbl_programs to setOf(BaseItemKind.LIVE_TV_PROGRAM),
			R.string.channels to setOf(BaseItemKind.LIVE_TV_CHANNEL),
			R.string.lbl_playlists to setOf(BaseItemKind.PLAYLIST),
			R.string.lbl_artists to setOf(BaseItemKind.MUSIC_ARTIST),
			R.string.lbl_albums to setOf(BaseItemKind.MUSIC_ALBUM),
			R.string.lbl_songs to setOf(BaseItemKind.AUDIO),
			R.string.photo_albums to setOf(BaseItemKind.PHOTO_ALBUM),
			R.string.photos to setOf(BaseItemKind.PHOTO),
			R.string.lbl_collections to setOf(BaseItemKind.BOX_SET),
			R.string.lbl_people to setOf(BaseItemKind.PERSON),
		)
	}

	private var searchJob: Job? = null

	private var previousQuery: String? = null

	private val _searchResultsFlow = MutableStateFlow<Collection<SearchResultGroup>>(emptyList())
	val searchResultsFlow = _searchResultsFlow.asStateFlow()

	fun searchImmediately(query: String) = searchDebounced(query, 0.milliseconds)

	fun searchDebounced(query: String, debounce: Duration = debounceDuration): Boolean {
		val trimmed = query.trim()
		if (trimmed == previousQuery) return false
		previousQuery = trimmed

		searchJob?.cancel()

		if (trimmed.isBlank()) {
			_searchResultsFlow.value = emptyList()
			return true
		}

		searchJob = viewModelScope.launch {
			delay(debounce)

			val enableMultiServer = userPreferences[UserPreferences.enableMultiServerLibraries]

			val jellyfinResults = groups.map { (stringRes, itemKinds) ->
				async {
					val result = if (enableMultiServer) {
						searchRepository.searchMultiServer(trimmed, itemKinds)
					} else {
						searchRepository.search(trimmed, itemKinds)
					}
					val items = result.getOrNull().orEmpty()
					val filteredItems = items.filter { item ->
						!parentalControlsRepository.shouldFilterItem(item)
					}
					SearchResultGroup(stringRes, filteredItems)
				}
			}.awaitAll()

		val allResults = if (jellyseerrRepository.isAvailable.value) {
			val jellyseerrResult = ErrorHandler.catchingWarning("search Jellyseerr") {
				jellyseerrRepository.search(trimmed)
			}
			val jellyseerrItems = jellyseerrResult.getOrNull()?.getOrNull()?.results?.map { it.toBaseItemDto() } ?: emptyList()
			val filteredJellyseerrItems = jellyseerrItems.filter { item ->
				!parentalControlsRepository.shouldFilterItem(item)
			}
			if (filteredJellyseerrItems.isNotEmpty()) {
				jellyfinResults + listOf(SearchResultGroup(R.string.jellyseerr_search_results, filteredJellyseerrItems))
			} else {
				jellyfinResults
			}
		} else {
			jellyfinResults
		}

		_searchResultsFlow.value = allResults
		}

		return true
	}
}
