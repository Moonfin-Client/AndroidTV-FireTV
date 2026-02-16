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
import org.jellyfin.androidtv.util.sdk.ApiClientFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID

data class MusicBrowseUiState(
	val isLoading: Boolean = true,
	val libraryName: String = "",
	val latestAudio: List<BaseItemDto> = emptyList(),
	val lastPlayed: List<BaseItemDto> = emptyList(),
	val favoriteAlbums: List<BaseItemDto> = emptyList(),
	val playlists: List<BaseItemDto> = emptyList(),
	val focusedItem: BaseItemDto? = null,
)

class MusicBrowseViewModel(
	private val api: ApiClient,
	private val apiClientFactory: ApiClientFactory,
) : ViewModel() {

	private val _uiState = MutableStateFlow(MusicBrowseUiState())
	val uiState: StateFlow<MusicBrowseUiState> = _uiState.asStateFlow()

	var effectiveApi: ApiClient = api
		private set

	private var folder: BaseItemDto? = null

	fun initialize(folderJson: String, serverId: UUID?, userId: UUID?) {
		val folder = kotlinx.serialization.json.Json.decodeFromString(
			BaseItemDto.serializer(), folderJson
		)
		this.folder = folder

		// Resolve correct API client for multi-server
		if (serverId != null) {
			val serverApi = if (userId != null) {
				apiClientFactory.getApiClient(serverId, userId)
			} else {
				apiClientFactory.getApiClientForServer(serverId)
			}
			if (serverApi != null) effectiveApi = serverApi
		}

		_uiState.value = MusicBrowseUiState(
			isLoading = true,
			libraryName = folder.name ?: "",
		)

		loadAllRows()
	}

	fun setFocusedItem(item: BaseItemDto) {
		_uiState.value = _uiState.value.copy(focusedItem = item)
	}

	private fun loadAllRows() {
		val parentId = folder?.id ?: return

		viewModelScope.launch {
			try {
				// Load all rows in parallel
				val latestJob = launch { loadLatestAudio(parentId) }
				val lastPlayedJob = launch { loadLastPlayed(parentId) }
				val favoritesJob = launch { loadFavoriteAlbums(parentId) }
				val playlistsJob = launch { loadPlaylists() }

				latestJob.join()
				lastPlayedJob.join()
				favoritesJob.join()
				playlistsJob.join()

				_uiState.value = _uiState.value.copy(isLoading = false)
			} catch (err: Exception) {
				Timber.e(err, "Failed to load music library rows")
				_uiState.value = _uiState.value.copy(isLoading = false)
			}
		}
	}

	private suspend fun loadLatestAudio(parentId: UUID) {
		try {
			val items = withContext(Dispatchers.IO) {
				effectiveApi.userLibraryApi.getLatestMedia(
					fields = ItemRepository.itemFields,
					parentId = parentId,
					limit = 50,
					imageTypeLimit = 1,
					includeItemTypes = setOf(BaseItemKind.AUDIO),
					groupItems = true,
				).content
			}
			_uiState.value = _uiState.value.copy(latestAudio = items)
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load latest audio")
		}
	}

	private suspend fun loadLastPlayed(parentId: UUID) {
		try {
			val response = withContext(Dispatchers.IO) {
				effectiveApi.itemsApi.getItems(
					fields = ItemRepository.itemFields,
					includeItemTypes = setOf(BaseItemKind.AUDIO),
					recursive = true,
					parentId = parentId,
					imageTypeLimit = 1,
					filters = setOf(ItemFilter.IS_PLAYED),
					sortBy = setOf(ItemSortBy.DATE_PLAYED),
					sortOrder = setOf(SortOrder.DESCENDING),
					enableTotalRecordCount = false,
					limit = 50,
				).content
			}
			_uiState.value = _uiState.value.copy(lastPlayed = response.items)
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load last played")
		}
	}

	private suspend fun loadFavoriteAlbums(parentId: UUID) {
		try {
			val response = withContext(Dispatchers.IO) {
				effectiveApi.itemsApi.getItems(
					fields = ItemRepository.itemFields,
					includeItemTypes = setOf(BaseItemKind.MUSIC_ALBUM),
					recursive = true,
					parentId = parentId,
					imageTypeLimit = 1,
					filters = setOf(ItemFilter.IS_FAVORITE),
					sortBy = setOf(ItemSortBy.SORT_NAME),
				).content
			}
			_uiState.value = _uiState.value.copy(favoriteAlbums = response.items)
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load favorite albums")
		}
	}

	private suspend fun loadPlaylists() {
		try {
			val response = withContext(Dispatchers.IO) {
				effectiveApi.itemsApi.getItems(
					fields = ItemRepository.itemFields,
					includeItemTypes = setOf(BaseItemKind.PLAYLIST),
					imageTypeLimit = 1,
					recursive = true,
					sortBy = setOf(ItemSortBy.DATE_CREATED),
					sortOrder = setOf(SortOrder.DESCENDING),
				).content
			}
			_uiState.value = _uiState.value.copy(playlists = response.items)
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load playlists")
		}
	}
}
