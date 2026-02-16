package org.jellyfin.androidtv.ui.browsing.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.repository.MultiServerRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.browsing.genre.JellyfinGenreItem
import org.jellyfin.androidtv.util.sdk.ApiClientFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import timber.log.Timber
import java.util.UUID

enum class GenreSortOption(val label: String) {
	NAME_ASC("Name (A-Z)"),
	NAME_DESC("Name (Z-A)"),
	MOST_ITEMS("Most Items"),
	LEAST_ITEMS("Least Items"),
	RANDOM("Random"),
}

data class GenresGridUiState(
	val isLoading: Boolean = true,
	val title: String = "Genres",
	val genres: List<JellyfinGenreItem> = emptyList(),
	val totalGenres: Int = 0,
	val currentSort: GenreSortOption = GenreSortOption.NAME_ASC,
	val selectedLibraryId: UUID? = null,
	val selectedLibraryName: String? = null,
	val libraries: List<BaseItemDto> = emptyList(),
	val libraryServerNames: Map<UUID, String> = emptyMap(),
	val focusedGenre: JellyfinGenreItem? = null,
)

class GenresGridViewModel(
	private val api: ApiClient,
	private val apiClientFactory: ApiClientFactory,
	private val multiServerRepository: MultiServerRepository,
	private val userPreferences: UserPreferences,
) : ViewModel() {

	private val _uiState = MutableStateFlow(GenresGridUiState())
	val uiState: StateFlow<GenresGridUiState> = _uiState.asStateFlow()

	private var allGenres = mutableListOf<JellyfinGenreItem>()
	private var folder: BaseItemDto? = null
	var includeType: String? = null
		private set

	val sortOptions = GenreSortOption.entries.toList()

	fun initialize(folder: BaseItemDto?, includeType: String?) {
		this.folder = folder
		this.includeType = includeType

		val libraryName = folder?.name
		_uiState.value = GenresGridUiState(
			isLoading = true,
			title = if (libraryName != null) "Genres — $libraryName" else "Genres",
			selectedLibraryId = folder?.id,
			selectedLibraryName = libraryName,
		)

		viewModelScope.launch {
			loadData()
		}
	}

	fun setSortOption(option: GenreSortOption) {
		_uiState.value = _uiState.value.copy(currentSort = option)
		applySortAndFilter()
	}

	fun setLibraryFilter(library: BaseItemDto?) {
		_uiState.value = _uiState.value.copy(
			selectedLibraryId = library?.id,
			selectedLibraryName = library?.name,
			isLoading = true,
		)
		viewModelScope.launch {
			loadGenres()
		}
	}

	fun setFocusedGenre(genre: JellyfinGenreItem) {
		_uiState.value = _uiState.value.copy(focusedGenre = genre)
	}

	private suspend fun loadData() {
		val enableMultiServer = userPreferences[UserPreferences.enableMultiServerLibraries]
		if (enableMultiServer && _uiState.value.selectedLibraryId == null) {
			loadMultiServerGenres()
		} else {
			loadUserLibraries()
			loadGenres()
		}
	}

	private suspend fun loadMultiServerGenres() {
		try {
			val sessions = multiServerRepository.getLoggedInServers()
			if (sessions.isEmpty()) {
				loadUserLibraries()
				loadGenres()
				return
			}

			// Load user libraries from all servers
			val libraries = mutableListOf<BaseItemDto>()
			val serverNames = mutableMapOf<UUID, String>()
			sessions.forEach { session ->
				try {
					val views = withContext(Dispatchers.IO) {
						session.apiClient.userViewsApi.getUserViews().content
					}
					views.items
						.filter { it.collectionType in listOf(CollectionType.MOVIES, CollectionType.TVSHOWS) }
						.forEach {
							libraries.add(it)
							serverNames[it.id] = session.server.name
						}
				} catch (e: Exception) {
					Timber.e(e, "Failed to load libraries from server ${session.server.name}")
				}
			}
			_uiState.value = _uiState.value.copy(libraries = libraries, libraryServerNames = serverNames)

			allGenres.clear()

			val allServerGenres = coroutineScope {
				sessions.map { session ->
					async(Dispatchers.IO) {
						try {
							val genresResponse = session.apiClient.genresApi.getGenres(
								sortBy = setOf(ItemSortBy.SORT_NAME),
							).content
							genresResponse.items.map { genre ->
								async(Dispatchers.IO) {
									createGenreItem(genre, session.apiClient, session.server.id)
								}
							}.awaitAll().filterNotNull()
						} catch (e: Exception) {
							Timber.e(e, "Failed to load genres from server ${session.server.name}")
							emptyList()
						}
					}
				}.awaitAll().flatten()
			}

			// Merge genres with the same name
			val mergedGenres = allServerGenres
				.groupBy { it.name.lowercase() }
				.map { (_, genres) ->
					if (genres.size == 1) genres.first()
					else {
						val first = genres.first()
						first.copy(itemCount = genres.sumOf { it.itemCount }, serverId = null)
					}
				}

			allGenres.addAll(mergedGenres)
			applySortAndFilter()
		} catch (e: Exception) {
			Timber.e(e, "Failed to load multi-server genres")
			_uiState.value = _uiState.value.copy(isLoading = false)
		}
	}

	private suspend fun loadUserLibraries() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.userViewsApi.getUserViews().content
			}
			val libraries = response.items
				.filter { it.collectionType in listOf(CollectionType.MOVIES, CollectionType.TVSHOWS) }
			_uiState.value = _uiState.value.copy(libraries = libraries)
		} catch (e: Exception) {
			Timber.e(e, "Failed to load user libraries")
		}
	}

	private suspend fun loadGenres() {
		_uiState.value = _uiState.value.copy(isLoading = true)

		try {
			val selectedLibraryId = _uiState.value.selectedLibraryId
			val genresResponse = withContext(Dispatchers.IO) {
				api.genresApi.getGenres(
					parentId = selectedLibraryId,
					sortBy = setOf(ItemSortBy.SORT_NAME),
				).content
			}

			allGenres.clear()

			val genreItems = coroutineScope {
				genresResponse.items.map { genre ->
					async(Dispatchers.IO) {
						createGenreItem(genre, api, null)
					}
				}.awaitAll().filterNotNull()
			}

			allGenres.addAll(genreItems)
			applySortAndFilter()
		} catch (e: Exception) {
			Timber.e(e, "Failed to load genres")
			_uiState.value = _uiState.value.copy(isLoading = false)
		}
	}

	private suspend fun createGenreItem(
		genre: BaseItemDto,
		client: ApiClient,
		serverId: UUID?,
	): JellyfinGenreItem? {
		return try {
			val selectedLibraryId = _uiState.value.selectedLibraryId
			val itemsResponse = client.itemsApi.getItems(
				parentId = selectedLibraryId,
				genres = setOf(genre.name.orEmpty()),
				includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
				recursive = true,
				sortBy = setOf(ItemSortBy.RANDOM),
				limit = 1,
				imageTypes = setOf(ImageType.BACKDROP),
				enableTotalRecordCount = true,
				fields = ItemRepository.itemFields,
			).content

			val itemCount = itemsResponse.totalRecordCount ?: 0
			if (itemCount == 0) return null

			val backdropUrl = itemsResponse.items.firstOrNull()?.let { item ->
				if (!item.backdropImageTags.isNullOrEmpty()) {
					client.imageApi.getItemImageUrl(
						itemId = item.id,
						imageType = ImageType.BACKDROP,
						tag = item.backdropImageTags!!.first(),
						maxWidth = 780,
						quality = 80,
					)
				} else null
			}

			JellyfinGenreItem(
				id = genre.id,
				name = genre.name.orEmpty(),
				backdropUrl = backdropUrl,
				itemCount = itemCount,
				parentId = selectedLibraryId,
				serverId = serverId,
			)
		} catch (e: Exception) {
			Timber.w(e, "Failed to get info for genre ${genre.name}")
			null
		}
	}

	private fun applySortAndFilter() {
		val sorted = when (_uiState.value.currentSort) {
			GenreSortOption.NAME_ASC -> allGenres.sortedBy { it.name.lowercase() }
			GenreSortOption.NAME_DESC -> allGenres.sortedByDescending { it.name.lowercase() }
			GenreSortOption.MOST_ITEMS -> allGenres.sortedByDescending { it.itemCount }
			GenreSortOption.LEAST_ITEMS -> allGenres.sortedBy { it.itemCount }
			GenreSortOption.RANDOM -> allGenres.shuffled()
		}
		_uiState.value = _uiState.value.copy(
			isLoading = false,
			genres = sorted,
			totalGenres = sorted.size,
		)
	}
}
