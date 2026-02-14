package org.jellyfin.androidtv.ui.jellyseerr

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.databinding.HorizontalGridBrowseBinding
import org.jellyfin.androidtv.ui.itemhandling.JellyseerrMediaBaseRowItem
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.util.Utils
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

/**
 * Filter type for Jellyseerr browse-by functionality
 */
enum class BrowseFilterType {
	GENRE,
	NETWORK,
	STUDIO,
	KEYWORD
}

/**
 * Sort options for TMDB/Jellyseerr discover API
 */
data class JellyseerrSortOption(
	val name: String,
	val value: String
)

/**
 * Fragment for browsing Jellyseerr content filtered by genre, network, or studio
 * Uses same layout as BrowseGridFragment for consistent library-like appearance
 */
class JellyseerrBrowseByFragment : Fragment() {
	private val viewModel: JellyseerrViewModel by viewModel()
	private val navigationRepository: NavigationRepository by inject()
	private val backgroundService: BackgroundService by inject()
	
	private var filterId: Int = 0
	private var filterName: String = ""
	private var mediaType: String = "movie" // movie or tv
	private var filterType: BrowseFilterType = BrowseFilterType.GENRE
	
	private var binding: HorizontalGridBrowseBinding? = null
	private lateinit var gridAdapter: ArrayObjectAdapter
	private lateinit var gridPresenter: VerticalGridPresenter
	private var gridViewHolder: VerticalGridPresenter.ViewHolder? = null
	private var currentPage = 1
	private var totalPages = 1
	private var totalResults = 0
	private var isLoading = false
	
	// Sorting
	private var currentSortOption: JellyseerrSortOption = SORT_OPTIONS[0]
	private var sortButton: ImageButton? = null
	
	// Filtering
	private var showAvailableOnly: Boolean = false
	private var showRequestedOnly: Boolean = false
	private var filterButton: ImageButton? = null
	
	companion object {
		private const val ARG_FILTER_ID = "filter_id"
		private const val ARG_FILTER_NAME = "filter_name"
		private const val ARG_MEDIA_TYPE = "media_type"
		private const val ARG_FILTER_TYPE = "filter_type"
		private const val NUM_COLUMNS = 7
		
		// TMDB sort options
		val SORT_OPTIONS = listOf(
			JellyseerrSortOption("Popularity", "popularity.desc"),
			JellyseerrSortOption("Rating", "vote_average.desc"),
			JellyseerrSortOption("Release Date", "primary_release_date.desc"),
			JellyseerrSortOption("Title", "original_title.asc"),
			JellyseerrSortOption("Revenue", "revenue.desc")
		)
		
		fun newInstance(
			filterId: Int, 
			filterName: String, 
			mediaType: String,
			filterType: BrowseFilterType = BrowseFilterType.GENRE
		): JellyseerrBrowseByFragment {
			return JellyseerrBrowseByFragment().apply {
				arguments = Bundle().apply {
					putInt(ARG_FILTER_ID, filterId)
					putString(ARG_FILTER_NAME, filterName)
					putString(ARG_MEDIA_TYPE, mediaType)
					putString(ARG_FILTER_TYPE, filterType.name)
				}
			}
		}
	}
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		
		arguments?.let {
			filterId = it.getInt(ARG_FILTER_ID)
			filterName = it.getString(ARG_FILTER_NAME) ?: ""
			mediaType = it.getString(ARG_MEDIA_TYPE) ?: "movie"
			filterType = try {
				BrowseFilterType.valueOf(it.getString(ARG_FILTER_TYPE) ?: "GENRE")
			} catch (e: Exception) {
				BrowseFilterType.GENRE
			}
		}
	}
	
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = HorizontalGridBrowseBinding.inflate(inflater, container, false)
		return binding!!.root
	}
	
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		
		setupHeader()
		setupToolbar()
		setupGrid()
		loadContent()
	}
	
	override fun onDestroyView() {
		super.onDestroyView()
		binding = null
		gridViewHolder = null
		sortButton = null
	}
	
	private fun setupHeader() {
		binding?.apply {
			// Show filter name centered at top (always visible)
			filterLogo.text = filterName
			// Initially hide title and info row until an item is selected
			title.text = ""
			title.visibility = View.GONE
			infoRow.visibility = View.GONE
		}
	}
	
	private fun setupToolbar() {
		val context = context ?: return
		val toolbar = binding?.toolBar ?: return
		
		toolbar.visibility = View.VISIBLE
		toolbar.removeAllViews()
		
		val buttonSize = Utils.convertDpToPixel(context, 26)
		
		// Filter button
		filterButton = ImageButton(context, null, 0, R.style.Button_Icon).apply {
			setImageResource(R.drawable.ic_filter)
			maxHeight = buttonSize
			adjustViewBounds = true
			contentDescription = getString(R.string.lbl_filters)
			setOnClickListener { showFilterMenu() }
		}
		toolbar.addView(filterButton)
		
		// Sort button
		sortButton = ImageButton(context, null, 0, R.style.Button_Icon).apply {
			setImageResource(R.drawable.ic_sort)
			maxHeight = buttonSize
			adjustViewBounds = true
			contentDescription = getString(R.string.lbl_sort_by)
			setOnClickListener { showSortMenu() }
		}
		toolbar.addView(sortButton)
	}
	
	private fun showFilterMenu() {
		val context = context ?: return
		val toolbar = binding?.toolBar ?: return
		
		val filterMenu = PopupMenu(context, toolbar, Gravity.END)
		
		// Filter options
		filterMenu.menu.add(0, 0, 0, "Show All").isChecked = !showAvailableOnly && !showRequestedOnly
		filterMenu.menu.add(0, 1, 1, "Available Only").isChecked = showAvailableOnly
		filterMenu.menu.add(0, 2, 2, "Requested Only").isChecked = showRequestedOnly
		filterMenu.menu.setGroupCheckable(0, true, true)
		
		filterMenu.setOnMenuItemClickListener { item ->
			when (item.itemId) {
				0 -> {
					if (showAvailableOnly || showRequestedOnly) {
						showAvailableOnly = false
						showRequestedOnly = false
						loadContent()
					}
				}
				1 -> {
					if (!showAvailableOnly) {
						showAvailableOnly = true
						showRequestedOnly = false
						loadContent()
					}
				}
				2 -> {
					if (!showRequestedOnly) {
						showAvailableOnly = false
						showRequestedOnly = true
						loadContent()
					}
				}
			}
			item.isChecked = true
			true
		}
		filterMenu.show()
	}
	
	private fun showSortMenu() {
		val context = context ?: return
		val toolbar = binding?.toolBar ?: return
		
		val sortMenu = PopupMenu(context, toolbar, Gravity.END)
		
		SORT_OPTIONS.forEachIndexed { index, option ->
			val item = sortMenu.menu.add(0, index, index, option.name)
			item.isChecked = option.value == currentSortOption.value
		}
		sortMenu.menu.setGroupCheckable(0, true, true)
		
		sortMenu.setOnMenuItemClickListener { item ->
			val selectedOption = SORT_OPTIONS[item.itemId]
			if (selectedOption.value != currentSortOption.value) {
				currentSortOption = selectedOption
				item.isChecked = true
				// Reload with new sort
				loadContent()
			}
			true
		}
		sortMenu.show()
	}
	
	private fun setupGrid() {
		gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM, false).apply {
			numberOfColumns = NUM_COLUMNS
			shadowEnabled = false
		}
		
		gridAdapter = ArrayObjectAdapter(CardPresenter())
		
gridPresenter.setOnItemViewSelectedListener(OnItemViewSelectedListener { 
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row? ->
			val discoverItem = (item as? JellyseerrMediaBaseRowItem)?.item
			if (discoverItem != null) {
				onItemSelected(discoverItem)
				
				val position = gridAdapter.indexOf(item)
				if (position >= gridAdapter.size() - 10 && !isLoading && currentPage < totalPages) {
					loadMoreContent()
				}
			}
		})
		
		gridPresenter.setOnItemViewClickedListener(OnItemViewClickedListener { 
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row? ->
			val discoverItem = (item as? JellyseerrMediaBaseRowItem)?.item
			if (discoverItem != null) {
				onItemClicked(discoverItem)
			}
		})
		
		// Create grid view and add to container
		binding?.rowsFragment?.let { container ->
			gridViewHolder = gridPresenter.onCreateViewHolder(container) as VerticalGridPresenter.ViewHolder
			gridViewHolder?.let { holder ->
				val gridView = holder.gridView
				gridView.setNumColumns(NUM_COLUMNS)
				val verticalSpacing = Utils.convertDpToPixel(requireContext(), 40)
				gridView.setVerticalSpacing(verticalSpacing)
				
				// Remove horizontal padding, add top padding
				val topPadding = Utils.convertDpToPixel(requireContext(), 8)
				gridView.setPadding(0, topPadding, 0, 0)
				
				container.removeAllViews()
				container.addView(holder.view)
				gridPresenter.onBindViewHolder(holder, gridAdapter)
			}
		}
	}
	
	private fun onItemSelected(item: JellyseerrDiscoverItemDto) {
		// Update background with backdrop
		item.backdropPath?.let { backdropPath ->
			val backdropUrl = "https://image.tmdb.org/t/p/w1280$backdropPath"
			backgroundService.setBackgroundUrl(backdropUrl, org.jellyfin.androidtv.data.service.BlurContext.BROWSING)
		}
		
		// Update title on the left to show selected item name (filterLogo stays centered)
		binding?.title?.apply {
			text = item.title ?: item.name ?: "Unknown"
			visibility = View.VISIBLE
		}
		
		// Show and update info row with metadata
		binding?.infoRow?.visibility = View.VISIBLE
		updateInfoRow(item)
		
		// Update counter with position
		val position = gridAdapter.indexOf(item) + 1
		updateCounter(position)
	}
	
	private fun updateCounter(position: Int) {
		binding?.counter?.text = "$position | $totalResults"
	}
	
	private fun updateInfoRow(item: JellyseerrDiscoverItemDto) {
		val context = context ?: return
		val infoRow = binding?.infoRow ?: return
		
		// Clear existing views
		infoRow.removeAllViews()
		
		val metadataItems = mutableListOf<String>()
		
		// Year
		val year = item.releaseDate?.take(4) ?: item.firstAirDate?.take(4)
		year?.let { metadataItems.add(it) }
		
		// Rating (if available)
		item.voteAverage?.let { rating ->
			if (rating > 0) {
				metadataItems.add("★ %.1f".format(rating))
			}
		}
		
		// Media type
		val typeLabel = when (item.mediaType) {
			"movie" -> "Movie"
			"tv" -> getString(R.string.lbl_tv_series)
			else -> ""
		}
		if (typeLabel.isNotEmpty()) metadataItems.add(typeLabel)
		
		// Status indicator
		item.mediaInfo?.status?.let { status ->
			val statusText = when (status) {
				1 -> "Unknown"
				2 -> "Pending"
				3 -> "Processing"
				4 -> "Partially Available"
				5 -> "Available"
				else -> null
			}
			statusText?.let { metadataItems.add(it) }
		}
		
		// Add metadata text views
		metadataItems.forEachIndexed { index, text ->
			if (index > 0) {
				// Add separator
				val separator = android.widget.TextView(context).apply {
					this.text = " • "
					textSize = 14f
					setTextColor(android.graphics.Color.WHITE)
					alpha = 0.7f
				}
				infoRow.addView(separator)
			}
			
			val textView = android.widget.TextView(context).apply {
				this.text = text
				textSize = 14f
				setTextColor(android.graphics.Color.WHITE)
				alpha = 0.7f
			}
			infoRow.addView(textView)
		}
	}
	
	private fun updateStatusText() {
		val sortName = currentSortOption.name
		val filterTypeName = when (filterType) {
			BrowseFilterType.GENRE -> getString(R.string.lbl_genres)
			BrowseFilterType.NETWORK -> "Network"
			BrowseFilterType.STUDIO -> "Studio"
			BrowseFilterType.KEYWORD -> "Keyword"
		}
		val mediaTypeName = if (mediaType == "movie") getString(R.string.lbl_movies) else getString(R.string.lbl_tv_series)
		
		binding?.statusText?.text = "${getString(R.string.lbl_showing)} $mediaTypeName ${getString(R.string.lbl_from)} '$filterName' ${getString(R.string.lbl_sorted_by)} $sortName"
	}
	
	private fun loadContent() {
		if (isLoading) return
		isLoading = true
		currentPage = 1
		
		// Show loading state
		binding?.counter?.text = "..."
		
		lifecycleScope.launch {
			try {
				val result = fetchContent(currentPage)
				
				result?.getOrNull()?.let { page ->
					totalPages = page.totalPages ?: 1
					totalResults = page.totalResults ?: 0
					var items = page.results ?: emptyList()
					
					// Apply availability filter
					items = applyFilter(items)
					
					gridAdapter.clear()
					gridAdapter.addAll(0, items.map { JellyseerrMediaBaseRowItem(it) })
					
					// Update UI
					updateCounter(if (items.isNotEmpty()) 1 else 0)
					updateStatusText()
				}
			} catch (e: Exception) {
				Timber.e(e, "Failed to load content for $filterType: $filterName")
			} finally {
				isLoading = false
			}
		}
	}
	
	private fun loadMoreContent() {
		if (isLoading || currentPage >= totalPages) return
		isLoading = true
		currentPage++
		
		lifecycleScope.launch {
			try {
				val result = fetchContent(currentPage)
				
				result?.getOrNull()?.let { page ->
					var items = page.results ?: emptyList()
					
					// Apply availability filter
					items = applyFilter(items)
					
					gridAdapter.addAll(gridAdapter.size(), items.map { JellyseerrMediaBaseRowItem(it) })
				}
			} catch (e: Exception) {
				Timber.e(e, "Failed to load more content for $filterType: $filterName")
				currentPage-- // Revert page on failure
			} finally {
				isLoading = false
			}
		}
	}
	
	private fun applyFilter(items: List<JellyseerrDiscoverItemDto>): List<JellyseerrDiscoverItemDto> {
		// If no filter is active, return all items
		if (!showAvailableOnly && !showRequestedOnly) {
			return items
		}
		
		return items.filter { item ->
			val status = item.mediaInfo?.status
			when {
				// Available only: status 4 (partially available) or 5 (available)
				showAvailableOnly -> status == 4 || status == 5
				// Requested only: status 2 (pending) or 3 (processing)
				showRequestedOnly -> status == 2 || status == 3
				else -> true
			}
		}
	}
	
	private suspend fun fetchContent(page: Int): Result<org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverPageDto>? {
		return when (filterType) {
			BrowseFilterType.GENRE -> {
				if (mediaType == "movie") {
					viewModel.discoverMovies(page = page, sortBy = currentSortOption.value, genreId = filterId.toString())
				} else {
					viewModel.discoverTv(page = page, sortBy = currentSortOption.value, genreId = filterId.toString())
				}
			}
			BrowseFilterType.NETWORK -> {
				// Network filtering - TV only
				viewModel.discoverTv(page = page, sortBy = currentSortOption.value, networkId = filterId.toString())
			}
			BrowseFilterType.STUDIO -> {
				// Studio filtering - Movies only
				viewModel.discoverMovies(page = page, sortBy = currentSortOption.value, studioId = filterId.toString())
			}
			BrowseFilterType.KEYWORD -> {
				// Keyword filtering - works for both movies and TV
				if (mediaType == "movie") {
					viewModel.discoverMovies(page = page, sortBy = currentSortOption.value, keywords = filterId.toString())
				} else {
					viewModel.discoverTv(page = page, sortBy = currentSortOption.value, keywords = filterId.toString())
				}
			}
		}
	}
	
	private fun onItemClicked(item: JellyseerrDiscoverItemDto) {
		val itemJson = Json.encodeToString(JellyseerrDiscoverItemDto.serializer(), item)
		navigationRepository.navigate(Destinations.jellyseerrMediaDetails(itemJson))
	}
}
