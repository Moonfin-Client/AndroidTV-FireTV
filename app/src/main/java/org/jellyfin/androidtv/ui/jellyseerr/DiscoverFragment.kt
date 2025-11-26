package org.jellyfin.androidtv.ui.jellyseerr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.util.Debouncer
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

class DiscoverFragment : Fragment() {
	private val viewModel: JellyseerrViewModel by viewModel()
	private val backgroundService: BackgroundService by inject()
	
	private var titleTextView: TextView? = null
	private var summaryTextView: TextView? = null
	private var yearTextView: TextView? = null
	private var ratingTextView: TextView? = null
	private var mediaTypeTextView: TextView? = null
	private var rowsFragment: JellyseerrDiscoverRowsFragment? = null
	
	// Debouncers for smooth navigation - only update after user stops moving
	private val infoDebouncer by lazy { Debouncer(150.milliseconds, lifecycleScope) }
	private val backdropDebouncer by lazy { Debouncer(200.milliseconds, lifecycleScope) }

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val view = inflater.inflate(R.layout.fragment_jellyseerr_discover_new, container, false)

		titleTextView = view.findViewById(R.id.title_text)
		summaryTextView = view.findViewById(R.id.summary_text)
		yearTextView = view.findViewById(R.id.year_text)
		ratingTextView = view.findViewById(R.id.rating_text)
		mediaTypeTextView = view.findViewById(R.id.media_type_text)

		val toolbarView = view.findViewById<ComposeView>(R.id.toolbar)
		toolbarView.setContent {
			MainToolbar(
				activeButton = MainToolbarActiveButton.Jellyseerr
			)
		}
		
		val toolbarContainer = view.findViewById<View>(R.id.toolbar_actions)
		toolbarContainer?.apply {
			isFocusable = true
			isFocusableInTouchMode = false
			setOnFocusChangeListener { _, hasFocus ->
				if (hasFocus) {
					toolbarView.requestFocus()
				}
			}
		}

		return view
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// Get reference to rows fragment
		rowsFragment = childFragmentManager.findFragmentById(R.id.jellyseerr_browse) as? JellyseerrDiscoverRowsFragment

		// Observe selected item to update header
		rowsFragment?.selectedItemStateFlow
			?.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			?.onEach { item ->
				if (item != null) {
					// Debounce info updates - only update after user stops navigating for 150ms
					infoDebouncer.debounce {
						updateItemInfo(item)
					}
					
					// Debounce backdrop loading - only load after user stops navigating for 200ms
					backdropDebouncer.debounce {
						loadBackdropImage(item)
					}
				}
			}
			?.launchIn(lifecycleScope)
	}

	override fun onResume() {
		super.onResume()
		
		// Request focus on rows fragment when this fragment becomes visible
		// This is crucial for when returning from details screen
		view?.postDelayed({
			if (isResumed) {
				rowsFragment?.view?.requestFocus()
				Timber.d("DiscoverFragment: Requesting focus on rows fragment")
			}
		}, 100)
	}

	override fun onDestroyView() {
		super.onDestroyView()
		// Cancel any pending debounced actions
		infoDebouncer.cancel()
		backdropDebouncer.cancel()
		
		titleTextView = null
		summaryTextView = null
		yearTextView = null
		ratingTextView = null
		mediaTypeTextView = null
		rowsFragment = null
	}

	private fun updateItemInfo(item: JellyseerrDiscoverItemDto) {
		// Update title
		titleTextView?.text = item.title ?: item.name ?: "Unknown"
		titleTextView?.isVisible = true

		// Update description
		if (!item.overview.isNullOrEmpty()) {
			summaryTextView?.text = item.overview
			summaryTextView?.isVisible = true
		} else {
			summaryTextView?.isVisible = false
		}

		// Add year
		val year = item.releaseDate?.take(4) ?: item.firstAirDate?.take(4)
		if (!year.isNullOrEmpty()) {
			yearTextView?.text = "$year •"
			yearTextView?.isVisible = true
		} else {
			yearTextView?.isVisible = false
		}

		val rating = item.voteAverage
		if (rating != null && rating > 0) {
			ratingTextView?.text = "★ %.1f •".format(rating)
			ratingTextView?.isVisible = true
		} else {
			ratingTextView?.isVisible = false
		}

		val mediaType = when (item.mediaType) {
			"movie" -> "Movie"
			"tv" -> "TV Series"
			else -> null
		}
		if (!mediaType.isNullOrEmpty()) {
			mediaTypeTextView?.text = mediaType
			mediaTypeTextView?.isVisible = true
		} else {
			mediaTypeTextView?.isVisible = false
		}

		// Load runtime for movies
		if (item.mediaType == "movie") {
			lifecycleScope.launch {
				try {
					val result = viewModel.getMovieDetails(item.id)
					if (result.isSuccess) {
						val movieDetails = result.getOrNull()
						val runtime = movieDetails?.runtime
						if (runtime != null && runtime > 0) {
							val hours = runtime / 60
							val minutes = runtime % 60
							val runtimeText = if (hours > 0) {
								if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
							} else {
								"${minutes}m"
							}
							mediaTypeTextView?.text = "$mediaType • $runtimeText"
						}
					}
				} catch (e: Exception) {
					Timber.e(e, "Failed to load movie details for runtime")
				}
			}
		}

		// Load number of seasons for TV series
		if (item.mediaType == "tv") {
			lifecycleScope.launch {
				try {
					val result = viewModel.getTvDetails(item.id)
					if (result.isSuccess) {
						val tvDetails = result.getOrNull()
						val seasons = tvDetails?.numberOfSeasons
						if (seasons != null && seasons > 0) {
							val seasonText = if (seasons == 1) "1 Season" else "$seasons Seasons"
							mediaTypeTextView?.text = "$mediaType • $seasonText"
						}
					}
				} catch (e: Exception) {
					Timber.e(e, "Failed to load TV details for seasons count")
				}
			}
		}
	}

	private fun loadBackdropImage(item: JellyseerrDiscoverItemDto) {
		val imageUrl = if (item.backdropPath != null) {
			"https://image.tmdb.org/t/p/w1280${item.backdropPath}"
		} else if (item.posterPath != null) {
			"https://image.tmdb.org/t/p/w1280${item.posterPath}"
		} else {
			null
		}

		if (imageUrl != null) {
			Timber.d("Loading backdrop for: ${item.title ?: item.name} - URL: $imageUrl")
			backgroundService.setBackgroundUrl(imageUrl, enableBlur = true)
		} else {
			Timber.d("No backdrop available for: ${item.title ?: item.name}")
			backgroundService.clearBackgrounds()
		}
	}
}
