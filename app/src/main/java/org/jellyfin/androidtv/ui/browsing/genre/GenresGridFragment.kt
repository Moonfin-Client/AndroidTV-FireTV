package org.jellyfin.androidtv.ui.browsing.genre

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.apiclient.ioCallContent
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
import org.koin.android.ext.android.inject
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID

/**
 * Sort options for the genres grid
 */
enum class GenreSortOption(val labelResId: Int, val sortBy: ItemSortBy?) {
	NAME_ASC(R.string.sort_a_z, ItemSortBy.SORT_NAME),
	NAME_DESC(R.string.sort_z_a, ItemSortBy.SORT_NAME),
	MOST_ITEMS(R.string.sort_most_items, null),
	LEAST_ITEMS(R.string.sort_least_items, null),
	RANDOM(R.string.sort_random, ItemSortBy.RANDOM),
}

/**
 * Fragment that displays all genres as a grid of wide cards with backdrop images.
 * Supports filtering by library and sorting options.
 */
class GenresGridFragment : Fragment() {
	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()

	private var loadingIndicator: ProgressBar? = null
	private var emptyText: TextView? = null
	private var gridContainer: View? = null
	private var counterText: TextView? = null
	private var libraryFilterContainer: LinearLayout? = null
	private var libraryFilterText: TextView? = null
	private var sortContainer: LinearLayout? = null
	private var sortText: TextView? = null

	private lateinit var gridAdapter: ArrayObjectAdapter
	private lateinit var gridPresenter: VerticalGridPresenter
	private var gridViewHolder: VerticalGridPresenter.ViewHolder? = null

	private var allGenres = mutableListOf<JellyfinGenreItem>()
	private var filteredGenres = mutableListOf<JellyfinGenreItem>()
	private var userLibraries = mutableListOf<BaseItemDto>()
	
	private var selectedLibraryId: UUID? = null
	private var selectedLibraryName: String? = null
	private var currentSortOption: GenreSortOption = GenreSortOption.NAME_ASC
	private var folder: BaseItemDto? = null
	private var includeType: String? = null

	companion object {
		private const val NUM_COLUMNS = 4
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		
		arguments?.getString(Extras.Folder)?.let { folderJson ->
			folder = Json.decodeFromString<BaseItemDto>(folderJson)
			selectedLibraryId = folder?.id
			selectedLibraryName = folder?.name
		}
		includeType = arguments?.getString(Extras.IncludeType)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View? {
		return inflater.inflate(R.layout.genres_grid_browse, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		
		loadingIndicator = view.findViewById(R.id.loadingIndicator)
		emptyText = view.findViewById(R.id.emptyText)
		gridContainer = view.findViewById(R.id.gridContainer)
		counterText = view.findViewById(R.id.counter)
		libraryFilterContainer = view.findViewById(R.id.libraryFilterContainer)
		libraryFilterText = view.findViewById(R.id.libraryFilterText)
		sortContainer = view.findViewById(R.id.sortContainer)
		sortText = view.findViewById(R.id.sortText)

		if (selectedLibraryName != null) {
			view.findViewById<TextView>(R.id.title)?.text = 
				"${getString(R.string.lbl_genres)} - $selectedLibraryName"
		}

		setupGrid()
		setupFilters()
		loadData()
	}

	private fun setupGrid() {
		val container = gridContainer as? ViewGroup ?: run {
			Timber.e("gridContainer is not a ViewGroup")
			return
		}
		
		gridPresenter = VerticalGridPresenter(0, false).apply {
			numberOfColumns = NUM_COLUMNS
		}
		gridAdapter = ArrayObjectAdapter(JellyfinGenreCardPresenter())

		gridPresenter.onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			val genre = item as? JellyfinGenreItem ?: return@OnItemViewClickedListener
			onGenreClicked(genre)
		}

		gridPresenter.onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
			val genre = item as? JellyfinGenreItem ?: return@OnItemViewSelectedListener
			val position = filteredGenres.indexOf(genre)
			if (position >= 0) updateCounter(position)
		}

		// Create the grid view with proper null checks
		try {
			gridViewHolder = gridPresenter.onCreateViewHolder(container)
			gridViewHolder?.view?.let { gridView ->
				container.addView(gridView)
				gridPresenter.onBindViewHolder(gridViewHolder!!, gridAdapter)
				
				// Remove any internal padding from the grid
				gridView.setPadding(0, 0, 0, 0)
				
				// Ensure the grid view can receive focus
				gridView.isFocusable = true
				gridView.isFocusableInTouchMode = true
			}
		} catch (e: Exception) {
			Timber.e(e, "Failed to create grid view")
		}
	}

	private fun setupFilters() {
		libraryFilterContainer?.setOnClickListener { showLibraryFilterMenu() }
		libraryFilterContainer?.setOnFocusChangeListener { view, hasFocus ->
			view.alpha = if (hasFocus) 1.0f else 0.75f
		}

		sortContainer?.setOnClickListener { showSortMenu() }
		sortContainer?.setOnFocusChangeListener { view, hasFocus ->
			view.alpha = if (hasFocus) 1.0f else 0.75f
		}

		sortText?.text = getString(currentSortOption.labelResId)
		libraryFilterText?.text = selectedLibraryName ?: getString(R.string.all_libraries)
	}

	private fun showLibraryFilterMenu() {
		val anchor = libraryFilterContainer ?: return
		val popup = PopupMenu(requireContext(), anchor)
		
		popup.menu.add(0, -1, 0, R.string.all_libraries)
		
		userLibraries.forEachIndexed { index, library ->
			popup.menu.add(0, index, index + 1, library.name ?: "Library")
		}
		
		popup.setOnMenuItemClickListener { menuItem ->
			if (menuItem.itemId == -1) {
				selectedLibraryId = null
				selectedLibraryName = null
				libraryFilterText?.text = getString(R.string.all_libraries)
			} else {
				val library = userLibraries.getOrNull(menuItem.itemId)
				selectedLibraryId = library?.id
				selectedLibraryName = library?.name
				libraryFilterText?.text = library?.name ?: getString(R.string.all_libraries)
			}
			loadGenres()
			true
		}
		
		popup.show()
	}

	private fun showSortMenu() {
		val anchor = sortContainer ?: return
		val popup = PopupMenu(requireContext(), anchor)
		
		GenreSortOption.entries.forEachIndexed { index, option ->
			popup.menu.add(0, index, index, option.labelResId)
		}
		
		popup.setOnMenuItemClickListener { menuItem ->
			currentSortOption = GenreSortOption.entries.getOrNull(menuItem.itemId) 
				?: GenreSortOption.NAME_ASC
			sortText?.text = getString(currentSortOption.labelResId)
			applySortAndFilter()
			true
		}
		
		popup.show()
	}

	private fun loadData() {
		lifecycleScope.launch {
			showLoading(true)
			
			loadUserLibraries()
			loadGenres()
		}
	}

	private suspend fun loadUserLibraries() {
		try {
			val response = apiClient.ioCallContent { userViewsApi.getUserViews() }
			userLibraries.clear()
			
			// Only include video libraries (movies and TV shows)
			response.items
				.filter { it.collectionType in listOf(CollectionType.MOVIES, CollectionType.TVSHOWS) }
				.let { userLibraries.addAll(it) }
				
			Timber.d("Loaded ${userLibraries.size} user libraries")
		} catch (e: Exception) {
			Timber.e(e, "Failed to load user libraries")
		}
	}

	private fun loadGenres() {
		lifecycleScope.launch {
			showLoading(true)
			
			try {
				val genresResponse = withContext(Dispatchers.IO) {
					apiClient.ioCallContent {
						genresApi.getGenres(
							parentId = selectedLibraryId,
							sortBy = setOf(ItemSortBy.SORT_NAME),
						)
					}
				}

				allGenres.clear()
				
				val genreItems = coroutineScope {
					genresResponse.items.map { genre ->
						async(Dispatchers.IO) {
							createGenreItem(genre)
						}
					}.awaitAll().filterNotNull()
				}
				
				allGenres.addAll(genreItems)
				
				Timber.d("Loaded ${allGenres.size} genres")
				
				applySortAndFilter()
				
			} catch (e: Exception) {
				Timber.e(e, "Failed to load genres")
				showLoading(false)
				showEmpty(true)
			}
		}
	}

	/**
	 * Creates a JellyfinGenreItem with backdrop URL and item count.
	 * Returns null if the genre has no items (empty genres are hidden).
	 * Uses a single API call to get both count and backdrop image.
	 * Note: This should be called from a coroutine context (IO dispatcher).
	 */
	private suspend fun createGenreItem(genre: BaseItemDto): JellyfinGenreItem? {
		return try {
			val itemsResponse = apiClient.ioCallContent {
				itemsApi.getItems(
					parentId = selectedLibraryId,
					genres = setOf(genre.name.orEmpty()),
					includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
					recursive = true,
					sortBy = setOf(ItemSortBy.RANDOM),
					limit = 1,
					imageTypes = setOf(ImageType.BACKDROP),
					enableTotalRecordCount = true,
					fields = ItemRepository.itemFields,
				)
			}

			val itemCount = itemsResponse.totalRecordCount ?: 0
			
			if (itemCount == 0) {
				return null
			}

			val backdropUrl = itemsResponse.items.firstOrNull()?.let { item ->
				if (!item.backdropImageTags.isNullOrEmpty()) {
					apiClient.imageApi.getItemImageUrl(
						itemId = item.id,
						imageType = ImageType.BACKDROP,
						tag = item.backdropImageTags!!.first(),
						maxWidth = 780,
						quality = 80
					)
				} else null
			}

			JellyfinGenreItem(
				id = genre.id,
				name = genre.name.orEmpty(),
				backdropUrl = backdropUrl,
				itemCount = itemCount,
				parentId = selectedLibraryId,
			)
		} catch (e: Exception) {
			Timber.w(e, "Failed to get info for genre ${genre.name}")
			null
		}
	}

	private fun applySortAndFilter() {
		filteredGenres.clear()
		filteredGenres.addAll(allGenres)
		
		when (currentSortOption) {
			GenreSortOption.NAME_ASC -> filteredGenres.sortBy { it.name.lowercase() }
			GenreSortOption.NAME_DESC -> filteredGenres.sortByDescending { it.name.lowercase() }
			GenreSortOption.MOST_ITEMS -> filteredGenres.sortByDescending { it.itemCount }
			GenreSortOption.LEAST_ITEMS -> filteredGenres.sortBy { it.itemCount }
			GenreSortOption.RANDOM -> filteredGenres.shuffle()
		}
		
		gridAdapter.clear()
		gridAdapter.addAll(0, filteredGenres)
		updateCounter(0)
		
		showLoading(false)
		showEmpty(filteredGenres.isEmpty())
		
		if (filteredGenres.isNotEmpty()) {
			gridViewHolder?.view?.requestFocus()
		}
	}

	private fun updateCounter(selectedPosition: Int) {
		val total = filteredGenres.size
		counterText?.text = "${selectedPosition + 1} | $total"
	}

	private fun onGenreClicked(genre: JellyfinGenreItem) {
		Timber.d("Genre clicked: ${genre.name}")
		
		navigationRepository.navigate(
			Destinations.genreBrowse(
				genreName = genre.name,
				parentId = genre.parentId,
				includeType = includeType
			)
		)
	}

	private fun showLoading(show: Boolean) {
		loadingIndicator?.visibility = if (show) View.VISIBLE else View.GONE
		gridContainer?.visibility = if (show) View.GONE else View.VISIBLE
	}

	private fun showEmpty(show: Boolean) {
		emptyText?.visibility = if (show) View.VISIBLE else View.GONE
	}

	override fun onDestroyView() {
		super.onDestroyView()
		loadingIndicator = null
		emptyText = null
		gridContainer = null
		counterText = null
		libraryFilterContainer = null
		libraryFilterText = null
		sortContainer = null
		sortText = null
		gridViewHolder = null
	}
}
