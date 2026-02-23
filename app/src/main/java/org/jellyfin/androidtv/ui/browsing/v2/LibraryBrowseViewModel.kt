package org.jellyfin.androidtv.ui.browsing.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.constant.GridDirection
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.constant.PosterSize
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.repository.MultiServerRepository
import org.jellyfin.androidtv.preference.LibraryPreferences
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.androidtv.util.sdk.ApiClientFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID

data class SortOption(
	val name: String,
	val sortBy: ItemSortBy,
	val sortOrder: SortOrder,
)

data class LibraryBrowseUiState(
	val isLoading: Boolean = true,
	val libraryName: String = "",
	val collectionType: CollectionType? = null,
	val items: List<BaseItemDto> = emptyList(),
	val totalItems: Int = 0,
	val currentSortOption: SortOption = SortOption("Name", ItemSortBy.SORT_NAME, SortOrder.ASCENDING),
	val filterFavorites: Boolean = false,
	val filterUnwatched: Boolean = false,
	val startLetter: String? = null,
	val hasMoreItems: Boolean = false,
	val focusedItem: BaseItemDto? = null,
	val posterSize: PosterSize = PosterSize.MED,
	val imageType: ImageType = ImageType.POSTER,
	val gridDirection: GridDirection = GridDirection.VERTICAL,
	val isGenreMode: Boolean = false,
	val genreName: String? = null,
	val displayPreferencesId: String? = null,
	val parentItemId: UUID? = null,
)

class LibraryBrowseViewModel(
	private val api: ApiClient,
	private val apiClientFactory: ApiClientFactory,
	private val preferencesRepository: PreferencesRepository,
	private val multiServerRepository: MultiServerRepository,
	private val userPreferences: UserPreferences,
) : ViewModel() {

	private val _uiState = MutableStateFlow(LibraryBrowseUiState())
	val uiState: StateFlow<LibraryBrowseUiState> = _uiState.asStateFlow()

	var effectiveApi: ApiClient = api
		private set

	var serverId: UUID? = null
		private set

	private var folder: BaseItemDto? = null
	private var libraryPreferences: LibraryPreferences? = null
	private var currentPage = 0
	private var isLoadingMore = false
	private val pageSize = 100

	// Genre mode fields
	private var genreFilter: String? = null
	private var includeType: String? = null
	private var genreParentId: UUID? = null

	val sortOptions: List<SortOption> by lazy {
		buildList {
			add(SortOption("Name", ItemSortBy.SORT_NAME, SortOrder.ASCENDING))
			add(SortOption("Date Added", ItemSortBy.DATE_CREATED, SortOrder.DESCENDING))
			add(SortOption("Premiere Date", ItemSortBy.PREMIERE_DATE, SortOrder.DESCENDING))
			add(SortOption("Rating", ItemSortBy.OFFICIAL_RATING, SortOrder.ASCENDING))
			add(SortOption("Community Rating", ItemSortBy.COMMUNITY_RATING, SortOrder.DESCENDING))
			add(SortOption("Critic Rating", ItemSortBy.CRITIC_RATING, SortOrder.DESCENDING))
			if (folder?.collectionType == CollectionType.TVSHOWS) {
				add(SortOption("Last Played", ItemSortBy.SERIES_DATE_PLAYED, SortOrder.DESCENDING))
			} else {
				add(SortOption("Last Played", ItemSortBy.DATE_PLAYED, SortOrder.DESCENDING))
			}
			if (folder?.collectionType == CollectionType.MOVIES) {
				add(SortOption("Runtime", ItemSortBy.RUNTIME, SortOrder.ASCENDING))
			}
		}
	}

	fun initialize(folderJson: String, serverId: UUID?, userId: UUID?) {
		val folder = kotlinx.serialization.json.Json.decodeFromString(
			BaseItemDto.serializer(), folderJson
		)
		this.folder = folder
		this.serverId = serverId

		resolveApiClient(serverId, userId)

		// Set initial loading state immediately
		_uiState.value = LibraryBrowseUiState(
			isLoading = true,
			libraryName = folder.name ?: "",
			collectionType = folder.collectionType,
		)

		viewModelScope.launch {
			// Load library display preferences on IO thread
			val dispPrefId = folder.displayPreferencesId
			if (dispPrefId != null) {
				libraryPreferences = withContext(Dispatchers.IO) {
					preferencesRepository.getLibraryPreferences(dispPrefId, effectiveApi)
				}
			}

			// Apply saved preferences
			val savedSort = libraryPreferences?.get(LibraryPreferences.sortBy)
			val savedOrder = libraryPreferences?.get(LibraryPreferences.sortOrder)
			val savedFavorites = libraryPreferences?.get(LibraryPreferences.filterFavoritesOnly) ?: false
			val savedUnwatched = libraryPreferences?.get(LibraryPreferences.filterUnwatchedOnly) ?: false

			val initialSort = if (savedSort != null && savedOrder != null) {
				sortOptions.find { it.sortBy == savedSort }?.copy(sortOrder = savedOrder)
					?: SortOption("Name", ItemSortBy.SORT_NAME, SortOrder.ASCENDING)
			} else {
				SortOption("Name", ItemSortBy.SORT_NAME, SortOrder.ASCENDING)
			}

			val savedPosterSize = libraryPreferences?.get(LibraryPreferences.posterSize) ?: PosterSize.MED
			val savedImageType = libraryPreferences?.get(LibraryPreferences.imageType) ?: ImageType.POSTER
			val savedGridDirection = libraryPreferences?.get(LibraryPreferences.gridDirection) ?: GridDirection.VERTICAL

			_uiState.value = _uiState.value.copy(
				currentSortOption = initialSort,
				filterFavorites = savedFavorites,
				filterUnwatched = savedUnwatched,
				posterSize = savedPosterSize,
				imageType = savedImageType,
				gridDirection = savedGridDirection,
			)

			loadItems(reset = true)
		}
	}

	/**
	 * Initialize in genre mode — browse items filtered by a specific genre.
	 */
	fun initializeGenre(
		genreName: String,
		parentId: UUID?,
		includeType: String?,
		serverId: UUID?,
		userId: UUID?,
		displayPreferencesId: String? = null,
		parentItemId: UUID? = null,
	) {
		this.genreFilter = genreName
		this.genreParentId = parentId
		this.includeType = includeType
		this.serverId = serverId

		resolveApiClient(serverId, userId)

		// Set initial loading state
		_uiState.value = LibraryBrowseUiState(
			isLoading = true,
			libraryName = genreName,
			isGenreMode = true,
			genreName = genreName,
			displayPreferencesId = displayPreferencesId,
			parentItemId = parentItemId,
			currentSortOption = SortOption("Name", ItemSortBy.SORT_NAME, SortOrder.ASCENDING),
		)

		if (displayPreferencesId != null) {
			viewModelScope.launch {
				libraryPreferences = withContext(Dispatchers.IO) {
					preferencesRepository.getLibraryPreferences(displayPreferencesId, effectiveApi)
				}

				val savedPosterSize = libraryPreferences?.get(LibraryPreferences.posterSize) ?: PosterSize.MED
				val savedImageType = libraryPreferences?.get(LibraryPreferences.imageType) ?: ImageType.POSTER
				val savedGridDirection = libraryPreferences?.get(LibraryPreferences.gridDirection) ?: GridDirection.VERTICAL

				_uiState.value = _uiState.value.copy(
					posterSize = savedPosterSize,
					imageType = savedImageType,
					gridDirection = savedGridDirection,
				)

				loadItems(reset = true)
			}
		} else {
			loadItems(reset = true)
		}
	}

	private fun resolveApiClient(serverId: UUID?, userId: UUID?) {
		if (serverId != null) {
			val serverApi = if (userId != null) {
				apiClientFactory.getApiClient(serverId, userId)
			} else {
				apiClientFactory.getApiClientForServer(serverId)
			}
			if (serverApi != null) effectiveApi = serverApi
		}
	}

	fun setSortOption(sortOption: SortOption) {
		_uiState.value = _uiState.value.copy(currentSortOption = sortOption)
		savePreferences()
		loadItems(reset = true)
	}

	fun toggleFavorites() {
		_uiState.value = _uiState.value.copy(filterFavorites = !_uiState.value.filterFavorites)
		savePreferences()
		loadItems(reset = true)
	}

	fun toggleUnwatched() {
		_uiState.value = _uiState.value.copy(filterUnwatched = !_uiState.value.filterUnwatched)
		savePreferences()
		loadItems(reset = true)
	}

	fun setStartLetter(letter: String?) {
		_uiState.value = _uiState.value.copy(startLetter = letter)
		loadItems(reset = true)
	}

	fun loadMore() {
		if (!isLoadingMore && _uiState.value.hasMoreItems) {
			loadItems(reset = false)
		}
	}

	fun setFocusedItem(item: BaseItemDto) {
		_uiState.value = _uiState.value.copy(focusedItem = item)
	}

	/**
	 * Re-read display preferences (posterSize, imageType, gridDirection) without reloading items.
	 * Returns true if any display setting changed.
	 */
	fun refreshDisplayPreferences(): Boolean {
		val prefs = libraryPreferences ?: return false
		val newPosterSize = prefs.get(LibraryPreferences.posterSize)
		val newImageType = prefs.get(LibraryPreferences.imageType)
		val newGridDirection = prefs.get(LibraryPreferences.gridDirection)
		val changed = newPosterSize != _uiState.value.posterSize ||
			newImageType != _uiState.value.imageType ||
			newGridDirection != _uiState.value.gridDirection
		if (changed) {
			_uiState.value = _uiState.value.copy(
				posterSize = newPosterSize,
				imageType = newImageType,
				gridDirection = newGridDirection,
			)
		}
		return changed
	}

	private fun savePreferences() {
		val prefs = libraryPreferences ?: return
		viewModelScope.launch {
			prefs.set(LibraryPreferences.filterFavoritesOnly, _uiState.value.filterFavorites)
			prefs.set(LibraryPreferences.filterUnwatchedOnly, _uiState.value.filterUnwatched)
			prefs.set(LibraryPreferences.sortBy, _uiState.value.currentSortOption.sortBy)
			prefs.set(LibraryPreferences.sortOrder, _uiState.value.currentSortOption.sortOrder)
			prefs.commit()
		}
	}

	private fun loadItems(reset: Boolean) {
		val isGenre = _uiState.value.isGenreMode
		val folder = this.folder
		if (!isGenre && folder == null) return
		if (isLoadingMore && !reset) return

		viewModelScope.launch {
			if (reset) {
				currentPage = 0
				_uiState.value = _uiState.value.copy(isLoading = true, items = emptyList())
			}
			isLoadingMore = true

			try {
				val state = _uiState.value

				// Build filters
				val filters = buildSet {
					if (state.filterFavorites) add(ItemFilter.IS_FAVORITE)
					if (state.filterUnwatched) add(ItemFilter.IS_UNPLAYED)
				}

				val includeTypes: Set<BaseItemKind>?
				val excludeTypes: Set<BaseItemKind>?
				val recursive: Boolean
				val parentId: UUID?
				val genres: Set<String>?

				if (isGenre) {
					// Genre mode: filter by genre name
					parentId = genreParentId
					genres = genreFilter?.let { setOf(it) }
					recursive = true
					includeTypes = when (includeType) {
						"Movie" -> setOf(BaseItemKind.MOVIE)
						"Series" -> setOf(BaseItemKind.SERIES)
						else -> setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
					}
					excludeTypes = null
				} else {
					// Library mode
					parentId = folder!!.id
					genres = null

					val isLibraryRoot = folder.type == BaseItemKind.USER_VIEW ||
						folder.type == BaseItemKind.COLLECTION_FOLDER

					includeTypes = when {
						isLibraryRoot -> {
							when (folder.collectionType) {
								CollectionType.MOVIES -> setOf(BaseItemKind.MOVIE)
								CollectionType.TVSHOWS -> setOf(BaseItemKind.SERIES)
								CollectionType.MUSIC -> setOf(BaseItemKind.MUSIC_ALBUM)
								else -> null
							}
						}
						else -> null
					}

					// Only recurse when we have an includeItemTypes filter to
					// avoid returning every nested item in mixed collections
					recursive = isLibraryRoot && includeTypes != null

					excludeTypes = when {
						(folder.type == BaseItemKind.USER_VIEW || folder.type == BaseItemKind.COLLECTION_FOLDER) &&
							folder.collectionType == CollectionType.MOVIES -> setOf(BaseItemKind.BOX_SET)
						else -> null
					}
				}

				val response = withContext(Dispatchers.IO) {
					effectiveApi.itemsApi.getItems(
						parentId = parentId,
						genres = genres,
						includeItemTypes = includeTypes,
						excludeItemTypes = excludeTypes,
						collapseBoxSetItems = false,
						recursive = recursive,
						fields = ItemRepository.itemFields,
						sortBy = setOf(state.currentSortOption.sortBy),
						sortOrder = setOf(state.currentSortOption.sortOrder),
						filters = filters,
						startIndex = currentPage * pageSize,
						limit = pageSize,
						enableTotalRecordCount = true,
						nameStartsWith = state.startLetter,
					).content
				}

				val totalItems = response.totalRecordCount ?: 0
				val newItems = response.items

				// Annotate with serverId for cross-server support
				val annotatedItems = if (serverId != null) {
					newItems.map { item ->
						item.copy(serverId = serverId.toString())
					}
				} else {
					newItems
				}

				val allItems = if (reset) annotatedItems else _uiState.value.items + annotatedItems
				currentPage++

				_uiState.value = _uiState.value.copy(
					isLoading = false,
					items = allItems,
					totalItems = totalItems,
					hasMoreItems = allItems.size < totalItems,
				)
			} catch (err: ApiClientException) {
				Timber.e(err, "Failed to load library items")
				_uiState.value = _uiState.value.copy(isLoading = false)
			} finally {
				isLoadingMore = false
			}
		}
	}
}
