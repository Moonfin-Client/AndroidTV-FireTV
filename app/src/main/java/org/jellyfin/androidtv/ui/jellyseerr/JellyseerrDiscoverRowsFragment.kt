package org.jellyfin.androidtv.ui.jellyseerr

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
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
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrMediaInfoDto
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import kotlinx.serialization.json.Json

class JellyseerrDiscoverRowsFragment : RowsSupportFragment() {
	private val viewModel: JellyseerrViewModel by viewModel()
	private val navigationRepository: NavigationRepository by inject()
	private var hasSetupRows = false
	
	// Flow to track selected item for display in parent fragment
	private val _selectedItemStateFlow = MutableStateFlow<JellyseerrDiscoverItemDto?>(null)
	val selectedItemStateFlow: StateFlow<JellyseerrDiscoverItemDto?> = _selectedItemStateFlow.asStateFlow()
	
	// Track focus position for restoration
	private var lastFocusedPosition = 0
	private var lastFocusedSubPosition = 0
	private var isReturningFromDetail = false
	private var isRestoringPosition = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setupRows()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		
		// Configure vertical grid view for up navigation to toolbar
		verticalGridView?.apply {
			setOnKeyListener { _, keyCode, event ->
				if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
					if (selectedPosition == 0) {
						// Navigate to toolbar in parent fragment
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
						for (i in 0 until rowAdapter.size()) {
							if (rowAdapter.get(i) === item) {
								lastFocusedSubPosition = i
								Timber.d("JellyseerrDiscoverRowsFragment: Updated lastFocusedSubPosition to $i")
								break
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

		// Add rows
		val trendingHeader = HeaderItem(0, "Trending")
		val trendingAdapter = ArrayObjectAdapter(MediaCardPresenter())
		trendingAdapter.setItems(emptyList<JellyseerrDiscoverItemDto>(), null)
		rowsAdapter.add(ListRow(trendingHeader, trendingAdapter))

		val moviesHeader = HeaderItem(1, "Popular Movies")
		val moviesAdapter = ArrayObjectAdapter(MediaCardPresenter())
		rowsAdapter.add(ListRow(moviesHeader, moviesAdapter))

		val tvHeader = HeaderItem(2, "Popular TV Series")
		val tvAdapter = ArrayObjectAdapter(MediaCardPresenter())
		rowsAdapter.add(ListRow(tvHeader, tvAdapter))

		val upcomingMoviesHeader = HeaderItem(3, "Upcoming Movies")
		val upcomingMoviesAdapter = ArrayObjectAdapter(MediaCardPresenter())
		rowsAdapter.add(ListRow(upcomingMoviesHeader, upcomingMoviesAdapter))

		val upcomingTvHeader = HeaderItem(4, "Upcoming TV Series")
		val upcomingTvAdapter = ArrayObjectAdapter(MediaCardPresenter())
		rowsAdapter.add(ListRow(upcomingTvHeader, upcomingTvAdapter))

		val requestsHeader = HeaderItem(5, "Your Requests")
		val requestsAdapter = ArrayObjectAdapter(MediaCardPresenter())
		rowsAdapter.add(ListRow(requestsHeader, requestsAdapter))

		adapter = rowsAdapter
		
		setupObservers()
		loadContent()
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
		
		lifecycleScope.launch {
			viewModel.trending.collect { trending ->
				updateRow(0, trending)
			}
		}

		lifecycleScope.launch {
			viewModel.trendingMovies.collect { movies ->
				updateRow(1, movies)
			}
		}

		lifecycleScope.launch {
			viewModel.trendingTv.collect { tv ->
				updateRow(2, tv)
			}
		}

		lifecycleScope.launch {
			viewModel.upcomingMovies.collect { movies ->
				updateRow(3, movies)
			}
		}

		lifecycleScope.launch {
			viewModel.upcomingTv.collect { tv ->
				updateRow(4, tv)
			}
		}

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
				updateRow(5, requestsAsItems)
			}
		}
	}

	private fun updateRow(index: Int, items: List<JellyseerrDiscoverItemDto>) {
		val rowsAdapter = adapter as? ArrayObjectAdapter
		if (rowsAdapter != null && index < rowsAdapter.size()) {
			val listRow = rowsAdapter.get(index) as? ListRow
			listRow?.adapter?.let { rowAdapter ->
				if (rowAdapter is ArrayObjectAdapter) {
					rowAdapter.setItems(items, null)
				}
			}
		}
	}

	private fun loadContent() {
		viewModel.loadTrendingContent()
		viewModel.loadRequests()
	}

	private fun onContentSelected(item: JellyseerrDiscoverItemDto) {
		// Use navigation system for proper focus restoration and back button handling
		val itemJson = Json.encodeToString(JellyseerrDiscoverItemDto.serializer(), item)
		navigationRepository.navigate(Destinations.jellyseerrMediaDetails(itemJson))
	}
}
