package org.jellyfin.androidtv.ui.jellyseerr

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.JellyseerrRowType
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrMediaInfoDto
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.qualifier.named
import timber.log.Timber
import kotlinx.serialization.json.Json

class JellyseerrDiscoverRowsFragment : RowsSupportFragment() {
	private val viewModel: JellyseerrViewModel by viewModel()
	private val navigationRepository: NavigationRepository by inject()
	private val jellyseerrPreferences: JellyseerrPreferences by inject(named("global"))
	private var hasSetupRows = false
	
	// Flow to track selected item for display in parent fragment
	private val _selectedItemStateFlow = MutableStateFlow<JellyseerrDiscoverItemDto?>(null)
	val selectedItemStateFlow: StateFlow<JellyseerrDiscoverItemDto?> = _selectedItemStateFlow.asStateFlow()
	
	// Track focus position for restoration
	private var lastFocusedPosition = 0
	private var lastFocusedSubPosition = 0
	private var isReturningFromDetail = false
	private var isRestoringPosition = false

	// API pagination loading flags
	private var isLoadingRequests = false
	private var isLoadingTrending = false
	private var isLoadingMovies = false
	private var isLoadingUpcomingMovies = false
	private var isLoadingTv = false
	private var isLoadingUpcomingTv = false
	
	// Map to track which row index corresponds to which row type
	private val rowTypeToIndex = mutableMapOf<JellyseerrRowType, Int>()
	private val indexToRowType = mutableMapOf<Int, JellyseerrRowType>()

	companion object {
		private const val STATE_LAST_FOCUSED_POSITION = "last_focused_position"
		private const val STATE_LAST_FOCUSED_SUB_POSITION = "last_focused_sub_position"
		private const val STATE_IS_RETURNING_FROM_DETAIL = "is_returning_from_detail"
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		
		savedInstanceState?.let {
			lastFocusedPosition = it.getInt(STATE_LAST_FOCUSED_POSITION, 0)
			lastFocusedSubPosition = it.getInt(STATE_LAST_FOCUSED_SUB_POSITION, 0)
			isReturningFromDetail = it.getBoolean(STATE_IS_RETURNING_FROM_DETAIL, false)
			Timber.d("JellyseerrDiscoverRowsFragment: Restored state - position=$lastFocusedPosition, subPosition=$lastFocusedSubPosition, isReturning=$isReturningFromDetail")
		}
		
		setupRows()
		setupObservers()
	}
	
	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putInt(STATE_LAST_FOCUSED_POSITION, lastFocusedPosition)
		outState.putInt(STATE_LAST_FOCUSED_SUB_POSITION, lastFocusedSubPosition)
		outState.putBoolean(STATE_IS_RETURNING_FROM_DETAIL, isReturningFromDetail)
		Timber.d("JellyseerrDiscoverRowsFragment: Saved state - position=$lastFocusedPosition, subPosition=$lastFocusedSubPosition, isReturning=$isReturningFromDetail")
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		
		verticalGridView?.apply {
			// Intercept DPAD_LEFT before HorizontalGridView consumes it.
			// HorizontalGridView eats DPAD_LEFT even at position 0, so the only
			// way to transfer focus to the sidebar is to intercept first.
			setOnKeyInterceptListener { event ->
				if (event.action == KeyEvent.ACTION_DOWN &&
					event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
					val focusedView = findFocus()
					val horizontalGrid = findParentHorizontalGridView(focusedView)
					if (horizontalGrid != null && horizontalGrid.selectedPosition == 0) {
						try {
							val sidebar = activity?.findViewById<View>(R.id.sidebar_overlay)
							if (sidebar != null && sidebar.isVisible) {
								sidebar.requestFocus()
								return@setOnKeyInterceptListener true
							}
						} catch (_: Throwable) { }
					}
				}
				false
			}

			setOnKeyListener { _, keyCode, event ->
				if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
					if (selectedPosition == 0) {
						val toolbarContainer = activity?.findViewById<View>(R.id.toolbar_actions)
						if (toolbarContainer != null) {
							toolbarContainer.requestFocus()
							return@setOnKeyListener true
						}
					}
				}
				false
			}
		}
	}

	override fun onResume() {
		super.onResume()
		
		// Refresh content from Jellyseerr server when returning to screen
		// Skip if data is already loaded (e.g. returning from settings)
		if (!isReturningFromDetail && !viewModel.hasContent()) {
			loadContent()
		}
		
		// Restore focus position if returning from detail screen
		if (isReturningFromDetail) {
			Timber.d("JellyseerrDiscoverRowsFragment: onResume() called with isReturningFromDetail=true")
			Timber.d("JellyseerrDiscoverRowsFragment: Saved position to restore: row=$lastFocusedPosition, col=$lastFocusedSubPosition")
			
			view?.postDelayed({
				if (isResumed && adapter != null && verticalGridView != null) {
					Timber.d("JellyseerrDiscoverRowsFragment: Starting restoration process")
					
					// Validate positions are in bounds BEFORE setting any flags
					val savedPosition = lastFocusedPosition
					val savedSubPosition = lastFocusedSubPosition
					val adapterSize = adapter.size()
					
					Timber.d("JellyseerrDiscoverRowsFragment: Validating - row=$savedPosition (adapter size=$adapterSize), col=$savedSubPosition")
					
					if (savedPosition >= 0 && savedPosition < adapterSize) {
						// Set flag to prevent position updates during restoration
						isRestoringPosition = true
						Timber.d("JellyseerrDiscoverRowsFragment: isRestoringPosition=true")
						
						// Use ListRowPresenter.SelectItemViewHolderTask to restore both positions atomically
						Timber.d("JellyseerrDiscoverRowsFragment: Calling setSelectedPosition($savedPosition, false, SelectItemViewHolderTask($savedSubPosition))")
						setSelectedPosition(
							savedPosition,
							false,
							androidx.leanback.widget.ListRowPresenter.SelectItemViewHolderTask(savedSubPosition)
						)
						
						// Request focus
						if (!verticalGridView!!.hasFocus()) {
							Timber.d("JellyseerrDiscoverRowsFragment: Requesting focus on grid view")
							verticalGridView?.requestFocus()
						}
						
						Timber.d("JellyseerrDiscoverRowsFragment: Focus restored successfully, clearing flags after delay")
						
						// Clear the restoration flag after a delay to allow restoration to complete
						view?.postDelayed({
							isRestoringPosition = false
							isReturningFromDetail = false
							Timber.d("JellyseerrDiscoverRowsFragment: Restoration complete - flags cleared")
						}, 200)
					} else {
						Timber.w("JellyseerrDiscoverRowsFragment: Invalid position $savedPosition (adapter size: $adapterSize)")
						isReturningFromDetail = false
					}
				}
			}, 300) // Increased delay to ensure data is loaded
		}
	}

	private fun setupRows() {
		if (hasSetupRows) return
		hasSetupRows = true

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			if (item is JellyseerrDiscoverItemDto) {
				// Mark that we're navigating to detail screen (for focus restoration on back)
				isReturningFromDetail = true
				Timber.d("JellyseerrDiscoverRowsFragment: Item clicked - capturing position: row=$lastFocusedPosition, col=$lastFocusedSubPosition, currentSelectedPosition=${selectedPosition}")
				onContentSelected(item)
			} else if (item is org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrGenreDto) {
				// Genre card clicked - navigate to browse by genre
				// Determine media type based on row type
				val rowType = indexToRowType[lastFocusedPosition]
				val mediaType = when (rowType) {
					JellyseerrRowType.MOVIE_GENRES -> "movie"
					JellyseerrRowType.SERIES_GENRES -> "tv"
					else -> "movie"
				}
				isReturningFromDetail = true
				onGenreSelected(item, mediaType)
			} else if (item is org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrStudioDto) {
				// Studio card clicked - navigate to browse by studio
				isReturningFromDetail = true
				onStudioSelected(item)
			} else if (item is org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrNetworkDto) {
				// Network card clicked - navigate to browse by network
				isReturningFromDetail = true
				onNetworkSelected(item)
			}
		}
		
		onItemViewSelectedListener = OnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
			if (item is JellyseerrDiscoverItemDto) {
				_selectedItemStateFlow.value = item
			}
			
			Timber.d("JellyseerrDiscoverRowsFragment: onItemViewSelectedListener - selectedPosition=$selectedPosition, isRestoringPosition=$isRestoringPosition, isReturningFromDetail=$isReturningFromDetail")
			
			// Only update lastFocusedPosition if we're not in the middle of restoring position
			// This prevents the position from being overwritten during the restoration process
			if (!isRestoringPosition) {
				val newPosition = selectedPosition
				// Track position continuously as user navigates (same as HomeRowsFragment)
				lastFocusedPosition = newPosition
				
				Timber.d("JellyseerrDiscoverRowsFragment: Updated lastFocusedPosition to $newPosition")
				
				// Store the sub-position within the row (horizontal position)
				if (row is ListRow && item is JellyseerrDiscoverItemDto) {
					// Get the position of this item within its row
					val rowAdapter = row.adapter as? ArrayObjectAdapter
					if (rowAdapter != null) {
						// Find the index of this item in the row
						var itemPosition = -1
						for (i in 0 until rowAdapter.size()) {
							if (rowAdapter.get(i) === item) {
								itemPosition = i
								lastFocusedSubPosition = i
								Timber.d("JellyseerrDiscoverRowsFragment: Updated lastFocusedSubPosition to $i")
								break
							}
						}
						
						// Check if we need to load more items for pagination
						// Trigger API call when within last 10 items of the row
						if (itemPosition >= rowAdapter.size() - 10 && itemPosition >= 0 && rowAdapter.size() > 0) {
							val rowType = indexToRowType[newPosition]
							when (rowType) {
								JellyseerrRowType.RECENT_REQUESTS -> {
									// Requests are loaded once, no API pagination
								}
								JellyseerrRowType.TRENDING -> {
									if (!isLoadingTrending) {
										loadMoreTrending()
									}
								}
								JellyseerrRowType.POPULAR_MOVIES -> {
									if (!isLoadingMovies) {
										loadMoreMovies()
									}
								}
								JellyseerrRowType.UPCOMING_MOVIES -> {
									if (!isLoadingUpcomingMovies) {
										loadMoreUpcomingMovies()
									}
								}
								JellyseerrRowType.POPULAR_SERIES -> {
									if (!isLoadingTv) {
										loadMoreTv()
									}
								}
								JellyseerrRowType.UPCOMING_SERIES -> {
									if (!isLoadingUpcomingTv) {
										loadMoreUpcomingTv()
									}
								}
								else -> { }
							}
						}
					}
				}
			} else {
				Timber.d("JellyseerrDiscoverRowsFragment: Skipping position update because isRestoringPosition=true")
			}
		}

		// Create rows adapter
		val rowPresenter = PositionableListRowPresenter(requireContext()).apply {
			setSelectEffectEnabled(true)
		}
		val rowsAdapter = ArrayObjectAdapter(rowPresenter)

		// Get active rows from configuration (enabled rows in order)
		val activeRows = jellyseerrPreferences.activeRows
		Timber.d("JellyseerrDiscoverRowsFragment: Setting up ${activeRows.size} active rows")
		
		// Clear the mappings
		rowTypeToIndex.clear()
		indexToRowType.clear()
		
		// Add rows based on configuration
		activeRows.forEachIndexed { index, rowType ->
			rowTypeToIndex[rowType] = index
			indexToRowType[index] = rowType
			
			val (headerTitle, presenter) = when (rowType) {
				JellyseerrRowType.RECENT_REQUESTS -> getString(R.string.jellyseerr_row_recent_requests) to MediaCardPresenter()
				JellyseerrRowType.TRENDING -> getString(R.string.jellyseerr_row_trending) to MediaCardPresenter()
				JellyseerrRowType.POPULAR_MOVIES -> getString(R.string.jellyseerr_row_popular_movies) to MediaCardPresenter()
				JellyseerrRowType.MOVIE_GENRES -> getString(R.string.jellyseerr_row_movie_genres) to GenreCardPresenter()
				JellyseerrRowType.UPCOMING_MOVIES -> getString(R.string.jellyseerr_row_upcoming_movies) to MediaCardPresenter()
				JellyseerrRowType.STUDIOS -> getString(R.string.jellyseerr_row_studios) to NetworkStudioCardPresenter()
				JellyseerrRowType.POPULAR_SERIES -> getString(R.string.jellyseerr_row_popular_series) to MediaCardPresenter()
				JellyseerrRowType.SERIES_GENRES -> getString(R.string.jellyseerr_row_series_genres) to GenreCardPresenter()
				JellyseerrRowType.UPCOMING_SERIES -> getString(R.string.jellyseerr_row_upcoming_series) to MediaCardPresenter()
				JellyseerrRowType.NETWORKS -> getString(R.string.jellyseerr_row_networks) to NetworkStudioCardPresenter()
			}
			
			val header = HeaderItem(index.toLong(), headerTitle)
			val rowAdapter = ArrayObjectAdapter(presenter)
			rowsAdapter.add(ListRow(header, rowAdapter))
			
			Timber.d("JellyseerrDiscoverRowsFragment: Added row $index: $headerTitle (type=$rowType)")
		}

		adapter = rowsAdapter
	}

	private fun setupObservers() {
		// Observe loading state for errors
		lifecycleScope.launch {
			viewModel.loadingState.collect { state ->
				when (state) {
					is JellyseerrLoadingState.Error -> {
						Timber.e("Jellyseerr connection error: ${state.message}")
						Toast.makeText(
							requireContext(),
							R.string.jellyseerr_connection_error,
							Toast.LENGTH_LONG
						).show()
					}
					else -> {}
				}
			}
		}
		
		// Trending
		lifecycleScope.launch {
			viewModel.trending.collect { trending ->
				updateRowByType(JellyseerrRowType.TRENDING, trending)
			}
		}

		// Popular Movies
		lifecycleScope.launch {
			viewModel.trendingMovies.collect { movies ->
				updateRowByType(JellyseerrRowType.POPULAR_MOVIES, movies)
			}
		}

		// Popular Series
		lifecycleScope.launch {
			viewModel.trendingTv.collect { tv ->
				updateRowByType(JellyseerrRowType.POPULAR_SERIES, tv)
			}
		}

		// Movie Genres
		lifecycleScope.launch {
			viewModel.movieGenres.collect { genres ->
				updateRowGenericByType(JellyseerrRowType.MOVIE_GENRES, genres)
			}
		}

		// Series Genres
		lifecycleScope.launch {
			viewModel.tvGenres.collect { genres ->
				updateRowGenericByType(JellyseerrRowType.SERIES_GENRES, genres)
			}
		}

		// Studios
		lifecycleScope.launch {
			viewModel.studios.collect { studios ->
				updateRowGenericByType(JellyseerrRowType.STUDIOS, studios)
			}
		}

		// Networks
		lifecycleScope.launch {
			viewModel.networks.collect { networks ->
				updateRowGenericByType(JellyseerrRowType.NETWORKS, networks)
			}
		}

		// Upcoming Movies
		lifecycleScope.launch {
			viewModel.upcomingMovies.collect { movies ->
				updateRowByType(JellyseerrRowType.UPCOMING_MOVIES, movies)
			}
		}

		// Upcoming Series
		lifecycleScope.launch {
			viewModel.upcomingTv.collect { tv ->
				updateRowByType(JellyseerrRowType.UPCOMING_SERIES, tv)
			}
		}

		// Recent Requests
		lifecycleScope.launch {
			viewModel.userRequests.collect { requests ->
				val requestsAsItems = requests.map { request ->
					JellyseerrDiscoverItemDto(
						id = request.media?.tmdbId ?: request.media?.id ?: request.id,
						title = request.media?.title ?: request.media?.name ?: "Unknown",
						name = request.media?.name ?: request.media?.title ?: "Unknown",
						overview = request.media?.overview ?: "",
						releaseDate = request.media?.releaseDate,
						firstAirDate = request.media?.firstAirDate,
						mediaType = request.type,
						posterPath = request.media?.posterPath,
						backdropPath = request.media?.backdropPath,
						mediaInfo = JellyseerrMediaInfoDto(
							id = request.media?.id,
							tmdbId = request.media?.tmdbId,
							tvdbId = request.media?.tvdbId,
							status = request.media?.status,
							status4k = request.media?.status4k
						)
					)
				}.filter { it.posterPath != null || it.backdropPath != null }
				updateRowByType(JellyseerrRowType.RECENT_REQUESTS, requestsAsItems)
			}
		}
	}

	private fun updateRowByType(rowType: JellyseerrRowType, items: List<JellyseerrDiscoverItemDto>) {
		val index = rowTypeToIndex[rowType] ?: return
		Timber.d("JellyseerrDiscoverRowsFragment: updateRowByType called for type=$rowType (index=$index) with ${items.size} items")
		updateRow(index, items)
	}

	private fun updateRowGenericByType(rowType: JellyseerrRowType, items: List<Any>) {
		val index = rowTypeToIndex[rowType] ?: return
		updateRowGeneric(index, items)
	}

	private fun updateRow(index: Int, items: List<JellyseerrDiscoverItemDto>) {
		Timber.d("JellyseerrDiscoverRowsFragment: updateRow called for index=$index with ${items.size} items")
		val rowsAdapter = adapter as? ArrayObjectAdapter
		if (rowsAdapter != null && index < rowsAdapter.size()) {
			val listRow = rowsAdapter.get(index) as? ListRow
			listRow?.adapter?.let { rowAdapter ->
				if (rowAdapter is ArrayObjectAdapter) {
					Timber.d("JellyseerrDiscoverRowsFragment: Setting row $index - showing ${items.size} items")
					
					// If view is available, defer update to avoid modifying RecyclerView during layout
					// Otherwise, set items directly (needed for initial load before view is created)
					val currentView = view
					if (currentView != null) {
						currentView.post {
							rowAdapter.setItems(items, null)
						}
					} else {
						rowAdapter.setItems(items, null)
					}
				}
			}
		} else {
			Timber.w("JellyseerrDiscoverRowsFragment: Cannot update row $index - adapter=${rowsAdapter != null}, size=${rowsAdapter?.size()}")
		}
	}

	private fun updateRowGeneric(index: Int, items: List<Any>) {
		val rowsAdapter = adapter as? ArrayObjectAdapter
		if (rowsAdapter != null && index < rowsAdapter.size()) {
			val listRow = rowsAdapter.get(index) as? ListRow
			listRow?.adapter?.let { rowAdapter ->
				if (rowAdapter is ArrayObjectAdapter) {
					// If view is available, defer update to avoid modifying RecyclerView during layout
					// Otherwise, set items directly (needed for initial load before view is created)
					val currentView = view
					if (currentView != null) {
						currentView.post {
							rowAdapter.setItems(items, null)
						}
					} else {
						rowAdapter.setItems(items, null)
					}
				}
			}
		}
	}

	private fun loadMoreTrending() {
		if (isLoadingTrending) return
		isLoadingTrending = true
		Timber.d("JellyseerrDiscoverRowsFragment: Loading more trending via API")
		viewModel.loadNextTrendingPage()
		isLoadingTrending = false
	}

	private fun loadMoreMovies() {
		if (isLoadingMovies) return
		isLoadingMovies = true
		Timber.d("JellyseerrDiscoverRowsFragment: Loading more movies via API")
		viewModel.loadNextTrendingMoviesPage()
		isLoadingMovies = false
	}

	private fun loadMoreUpcomingMovies() {
		if (isLoadingUpcomingMovies) return
		isLoadingUpcomingMovies = true
		Timber.d("JellyseerrDiscoverRowsFragment: Loading more upcoming movies via API")
		viewModel.loadNextUpcomingMoviesPage()
		isLoadingUpcomingMovies = false
	}

	private fun loadMoreTv() {
		if (isLoadingTv) return
		isLoadingTv = true
		Timber.d("JellyseerrDiscoverRowsFragment: Loading more TV via API")
		viewModel.loadNextTrendingTvPage()
		isLoadingTv = false
	}

	private fun loadMoreUpcomingTv() {
		if (isLoadingUpcomingTv) return
		isLoadingUpcomingTv = true
		Timber.d("JellyseerrDiscoverRowsFragment: Loading more upcoming TV via API")
		viewModel.loadNextUpcomingTvPage()
		isLoadingUpcomingTv = false
	}

	/**
	 * Walk up the view hierarchy from the focused view to find the containing HorizontalGridView.
	 */
	private fun findParentHorizontalGridView(view: View?): androidx.leanback.widget.HorizontalGridView? {
		var current = view?.parent
		while (current != null) {
			if (current is androidx.leanback.widget.HorizontalGridView) return current
			current = current.parent
		}
		return null
	}

	private fun loadContent() {
		viewModel.loadTrendingContent()
		viewModel.loadGenres()
		viewModel.loadRequests()
	}

	private fun onContentSelected(item: JellyseerrDiscoverItemDto) {
		// Use navigation system for proper focus restoration and back button handling
		val itemJson = Json.encodeToString(JellyseerrDiscoverItemDto.serializer(), item)
		navigationRepository.navigate(Destinations.jellyseerrMediaDetails(itemJson))
	}

	private fun onGenreSelected(genre: org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrGenreDto, mediaType: String) {
		// Navigate to browse by genre fragment
		navigationRepository.navigate(Destinations.jellyseerrBrowseByGenre(genre.id, genre.name, mediaType))
	}

	private fun onStudioSelected(studio: org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrStudioDto) {
		// Navigate to browse by studio fragment (movies only)
		navigationRepository.navigate(Destinations.jellyseerrBrowseByStudio(studio.id, studio.name))
	}

	private fun onNetworkSelected(network: org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrNetworkDto) {
		// Navigate to browse by network fragment (TV only)
		navigationRepository.navigate(Destinations.jellyseerrBrowseByNetwork(network.id, network.name))
	}
}
