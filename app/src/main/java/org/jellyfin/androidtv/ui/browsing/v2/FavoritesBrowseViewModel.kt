package org.jellyfin.androidtv.ui.browsing.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.personsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import timber.log.Timber

data class FavoritesBrowseUiState(
	val isLoading: Boolean = true,
	val cast: List<BaseItemDto> = emptyList(),
	val movies: List<BaseItemDto> = emptyList(),
	val shows: List<BaseItemDto> = emptyList(),
	val episodes: List<BaseItemDto> = emptyList(),
	val playlists: List<BaseItemDto> = emptyList(),
	val focusedItem: BaseItemDto? = null,
)

class FavoritesBrowseViewModel(
	private val api: ApiClient,
) : ViewModel() {

	private val _uiState = MutableStateFlow(FavoritesBrowseUiState())
	val uiState: StateFlow<FavoritesBrowseUiState> = _uiState.asStateFlow()

	fun initialize() {
		_uiState.value = FavoritesBrowseUiState(isLoading = true)
		loadAllRows()
	}

	fun setFocusedItem(item: BaseItemDto) {
		_uiState.value = _uiState.value.copy(focusedItem = item)
	}

	private fun loadAllRows() {
		viewModelScope.launch {
			try {
				val castJob = launch { loadCast() }
				val moviesJob = launch { loadMovies() }
				val showsJob = launch { loadShows() }
				val episodesJob = launch { loadEpisodes() }
				val playlistsJob = launch { loadPlaylists() }

				castJob.join()
				moviesJob.join()
				showsJob.join()
				episodesJob.join()
				playlistsJob.join()

				_uiState.value = _uiState.value.copy(isLoading = false)
			} catch (err: Exception) {
				Timber.e(err, "Failed to load favorites")
				_uiState.value = _uiState.value.copy(isLoading = false)
			}
		}
	}

	private suspend fun loadCast() {
		try {
			val items = withContext(Dispatchers.IO) {
				api.personsApi.getPersons(
					isFavorite = true,
					fields = ItemRepository.itemFields,
				).content.items
			}
			_uiState.value = _uiState.value.copy(cast = items)
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load favorite cast")
		}
	}

	private suspend fun loadMovies() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.itemsApi.getItems(
					sortBy = setOf(ItemSortBy.SORT_NAME),
					filters = setOf(ItemFilter.IS_FAVORITE),
					includeItemTypes = setOf(BaseItemKind.MOVIE),
					recursive = true,
					fields = ItemRepository.itemFields,
				).content
			}
			_uiState.value = _uiState.value.copy(movies = response.items)
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load favorite movies")
		}
	}

	private suspend fun loadShows() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.itemsApi.getItems(
					sortBy = setOf(ItemSortBy.SORT_NAME),
					filters = setOf(ItemFilter.IS_FAVORITE),
					includeItemTypes = setOf(BaseItemKind.SERIES),
					recursive = true,
					fields = ItemRepository.itemFields,
				).content
			}
			_uiState.value = _uiState.value.copy(shows = response.items)
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load favorite shows")
		}
	}

	private suspend fun loadEpisodes() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.itemsApi.getItems(
					sortBy = setOf(ItemSortBy.SORT_NAME),
					filters = setOf(ItemFilter.IS_FAVORITE),
					includeItemTypes = setOf(BaseItemKind.EPISODE),
					recursive = true,
					fields = ItemRepository.itemFields,
				).content
			}
			_uiState.value = _uiState.value.copy(episodes = response.items)
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load favorite episodes")
		}
	}

	private suspend fun loadPlaylists() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.itemsApi.getItems(
					sortBy = setOf(ItemSortBy.SORT_NAME),
					filters = setOf(ItemFilter.IS_FAVORITE),
					includeItemTypes = setOf(BaseItemKind.PLAYLIST),
					recursive = true,
					fields = ItemRepository.itemFields,
				).content
			}
			_uiState.value = _uiState.value.copy(playlists = response.items)
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load favorite playlists")
		}
	}
}
