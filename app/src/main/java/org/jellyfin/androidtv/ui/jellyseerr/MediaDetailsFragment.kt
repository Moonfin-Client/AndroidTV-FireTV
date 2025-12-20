package org.jellyfin.androidtv.ui.jellyseerr

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.Outline
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.asDrawable
import coil3.load
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrMovieDetailsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrTvDetailsDto
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.TextUnderButton
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.util.dp
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

class MediaDetailsFragment : Fragment() {
	private val viewModel: JellyseerrViewModel by viewModel()
	private val imageLoader: ImageLoader by inject()
	private val backgroundService: BackgroundService by inject()
	private val navigationRepository: NavigationRepository by inject()
	private val apiClient: ApiClient by inject()
	
	private var selectedItem: JellyseerrDiscoverItemDto? = null
	private var movieDetails: JellyseerrMovieDetailsDto? = null
	private var tvDetails: JellyseerrTvDetailsDto? = null
	private var requestButton: View? = null
	private var request4kButton: View? = null
	private var trailerButton: View? = null
	private var playInMoonfinButton: View? = null
	private var castSection: View? = null
	private var toolbarContainer: View? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		
		// Get item from arguments (passed via navigation system)
		val itemJson = arguments?.getString("item")
		if (itemJson != null) {
			try {
				selectedItem = Json.decodeFromString<JellyseerrDiscoverItemDto>(itemJson)
			} catch (e: Exception) {
				Timber.e(e, "Failed to deserialize item from arguments")
			}
		}
		
		if (selectedItem == null) {
			Timber.e("MediaDetailsFragment: No item data found in arguments")
			Toast.makeText(requireContext(), "Error: Item data not found", Toast.LENGTH_SHORT).show()
			// Let navigation system handle going back
			requireActivity().onBackPressedDispatcher.onBackPressed()
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val mainContainer = FrameLayout(requireContext()).apply {
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			)
			setBackgroundColor(Color.parseColor("#111827"))
		}

		// Add toolbar on top of everything (fixed position)
		val toolbar = ComposeView(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT
			)
			id = View.generateViewId()
			elevation = 8.dp(context).toFloat()
			setContent {
				MainToolbar(
					activeButton = MainToolbarActiveButton.Jellyseerr
				)
			}
		}
		toolbarContainer = toolbar

		val scrollView = ScrollView(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT
			)
			setBackgroundColor(Color.parseColor("#111827"))
			isFocusable = true
			isFocusableInTouchMode = true
			isScrollbarFadingEnabled = false
			// Add top padding to account for toolbar height (approximately 80dp)
			setPadding(0, 80.dp(context), 0, 0)
			clipToPadding = false
		}

		val rootLayout = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
		}

		scrollView.addView(rootLayout)
		rootLayout.addView(createBackdropWithHeaderSection())
		
		mainContainer.addView(scrollView)
		mainContainer.addView(toolbar)
		
		return mainContainer
	}
	
	private fun createBackdropWithHeaderSection(): View {
		val container = FrameLayout(requireContext()).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
		}
		
		container.addView(createBackdropSection())
		
		val posterWrapper = FrameLayout(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.WRAP_CONTENT,
				FrameLayout.LayoutParams.WRAP_CONTENT
			).apply {
				leftMargin = 24.dp(context)
				topMargin = 24.dp(context)
			}
			elevation = 8.dp(context).toFloat()
		}
		posterWrapper.addView(createPosterSection())
		
		val headerWrapper = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 196.dp(context)
			}
		}
		
		val gradientFade = View(requireContext()).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				120.dp(context)
			)
			background = android.graphics.drawable.GradientDrawable(
				android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
				intArrayOf(
					Color.TRANSPARENT,
					Color.parseColor("#111827")
				)
			)
		}
		headerWrapper.addView(gradientFade)
		
		val headerContent = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			setBackgroundColor(Color.parseColor("#111827"))
			setPadding(24.dp(context), 0, 24.dp(context), 8.dp(context))
		}
		
		headerWrapper.addView(headerContent)
		container.addView(headerWrapper)
		
		val contentWrapper = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 316.dp(context)
			}
			setBackgroundColor(Color.parseColor("#111827"))
		}
		contentWrapper.addView(createOverviewSection())
		contentWrapper.addView(createCastSection())
		contentWrapper.addView(createSimilarSection())
		container.addView(contentWrapper)
		
		val titleWrapper = FrameLayout(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 230.dp(context)
				leftMargin = 248.dp(context)
			}
			elevation = 4.dp(context).toFloat()
		}
		titleWrapper.addView(createTitleSection())
		container.addView(titleWrapper)
		container.addView(posterWrapper)
		
		return container
	}
	
	private fun createTitleSection(): View {
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT
			)
		}

		container.addView(createStatusBadge())
		
		val title = when {
			movieDetails != null -> movieDetails!!.title
			tvDetails != null -> tvDetails!!.name
			else -> selectedItem?.title ?: "Unknown"
		}
		
		val year = when {
			movieDetails != null -> movieDetails!!.releaseDate?.take(4)
			tvDetails != null -> tvDetails!!.firstAirDate?.take(4)
			else -> selectedItem?.releaseDate?.take(4)
		}

		val titleText = TextView(requireContext()).apply {
			text = if (year != null) "$title ($year)" else title
			textSize = 24f  // Reduced from 32f
			setTextColor(Color.WHITE)
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 0.dp(context)
			}
		}
		container.addView(titleText)

		container.addView(createAttributesSection())
		
		return container
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		loadFullDetails()
		
		// Focus will be handled automatically by the first focusable element
		view.post {
			requestButton?.requestFocus()
		}
	}

	private fun loadFullDetails() {
		val item = selectedItem ?: return

		lifecycleScope.launch {
			try {
				if (item.mediaType == "movie") {
					movieDetails = viewModel.getMovieDetails(item.id).getOrNull()
					Timber.d("Loaded movie details: ${movieDetails?.title}")
				} else if (item.mediaType == "tv") {
					tvDetails = viewModel.getTvDetails(item.id).getOrNull()
					Timber.d("Loaded TV details: ${tvDetails?.name}")
				}
				
				view?.let { refreshUI() }
			} catch (e: Exception) {
				Timber.e(e, "Failed to load full details")
			}
		}
	}

	private fun refreshUI() {
		Timber.d("MediaDetailsFragment: refreshUI() called")
		val mainContainer = view as? FrameLayout
		val scrollView = mainContainer?.getChildAt(0) as? ScrollView
		val rootLayout = scrollView?.getChildAt(0) as? LinearLayout
		
		if (rootLayout != null) {
			Timber.d("MediaDetailsFragment: Refreshing UI with full details")
			rootLayout.removeAllViews()
			rootLayout.addView(createBackdropWithHeaderSection())
			
			view?.post {
				requestButton?.requestFocus()
			}
		} else {
			Timber.e("MediaDetailsFragment: Failed to get rootLayout for refresh - mainContainer: ${mainContainer != null}, scrollView: ${scrollView != null}")
		}
	}

	private fun createBackdropSection(): View {
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				400.dp(context)
			)
		}

		// Backdrop image
		val backdropImage = ImageView(requireContext()).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT
			)
			scaleType = ImageView.ScaleType.CENTER_CROP
		}

		// Load backdrop
		selectedItem?.backdropPath?.let { backdropPath ->
			val backdropUrl = "https://image.tmdb.org/t/p/w1280$backdropPath"
			lifecycleScope.launch {
				try {
					val request = ImageRequest.Builder(requireContext())
					.data(backdropUrl)
					.build()
				val result = imageLoader.execute(request)
				backdropImage.setImageDrawable(result.image?.asDrawable(resources))
				
				// Note: BackgroundService requires BaseItemDto or Server, cannot use TMDB URLs
			} catch (e: Exception) {
				Timber.e(e, "Failed to load backdrop")
			}
		}
	}		// Gradient overlay (simulating linear-gradient)
		val gradientOverlay = View(requireContext()).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT
			)
			setBackgroundColor(Color.parseColor("#80111827")) // Semi-transparent gray-900
		}

		container.addView(backdropImage)
		container.addView(gradientOverlay)
		
		return container
	}

	private fun createPosterSection(): View {
		val posterContainer = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				208.dp(context), // w-52 = 13rem = 208px
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				marginEnd = 16.dp(context)
			}
		}

		val posterImage = ImageView(requireContext()).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				312.dp(context) // 3:2 aspect ratio
			)
			scaleType = ImageView.ScaleType.FIT_CENTER
			setBackgroundColor(Color.parseColor("#1F2937")) // gray-800
		}

		// Load poster
		selectedItem?.posterPath?.let { posterPath ->
			val posterUrl = "https://image.tmdb.org/t/p/w500$posterPath"
			lifecycleScope.launch {
				try {
					val request = ImageRequest.Builder(requireContext())
						.data(posterUrl)
						.build()
					val result = imageLoader.execute(request)
					posterImage.setImageDrawable(result.image?.asDrawable(resources))
				} catch (e: Exception) {
					Timber.e(e, "Failed to load poster")
				}
			}
		}

		posterContainer.addView(posterImage)
		return posterContainer
	}

	/**
	 * Creates the title, status, and action buttons section
	 * Mimics: .media-title from Seerr
	 */
	/**
	 * Creates status badge
	 * Mimics: .media-status from Seerr
	 */
	private fun createStatusBadge(): View {
		// Get mediaInfo from full details if available, otherwise from selectedItem
		val mediaInfo = movieDetails?.mediaInfo ?: tvDetails?.mediaInfo ?: selectedItem?.mediaInfo
		val status = mediaInfo?.status
		val status4k = mediaInfo?.status4k
		
		// Check for declined requests
		val requests = mediaInfo?.requests
		val hdDeclined = requests?.any { !it.is4k && it.status == 3 } == true
		val fourKDeclined = requests?.any { it.is4k && it.status == 3 } == true
		
		// Determine what to show based on both statuses
		val (statusText, bgColor) = when {
			// Both HD and 4K declined
			hdDeclined && fourKDeclined -> "HD + 4K DECLINED" to Color.parseColor("#EF4444") // red-500
			// Only 4K declined
			fourKDeclined -> "4K DECLINED" to Color.parseColor("#EF4444") // red-500
			// Only HD declined
			hdDeclined -> "HD DECLINED" to Color.parseColor("#EF4444") // red-500
			
			// Both HD and 4K blacklisted
			status == 6 && status4k == 6 -> "HD + 4K BLACKLISTED" to Color.parseColor("#DC2626") // red-600
			// Only 4K blacklisted
			status4k == 6 -> "4K BLACKLISTED" to Color.parseColor("#DC2626") // red-600
			// Only HD blacklisted
			status == 6 -> "HD BLACKLISTED" to Color.parseColor("#DC2626") // red-600
			
			// Both HD and 4K available
			status == 5 && status4k == 5 -> "HD + 4K AVAILABLE" to Color.parseColor("#22C55E") // green-500
			// Only 4K available
			status4k == 5 -> "4K AVAILABLE" to Color.parseColor("#22C55E") // green-500
			// Only HD available
			status == 5 -> "HD AVAILABLE" to Color.parseColor("#22C55E") // green-500
			
			// Both HD and 4K partially available
			status == 4 && status4k == 4 -> "HD + 4K PARTIAL" to Color.parseColor("#22C55E") // green-500
			// Only 4K partially available
			status4k == 4 -> "4K PARTIAL" to Color.parseColor("#22C55E") // green-500
			// Only HD partially available
			status == 4 -> "HD PARTIAL" to Color.parseColor("#22C55E") // green-500
			
			// Both HD and 4K processing
			status == 3 && status4k == 3 -> "HD + 4K PROCESSING" to Color.parseColor("#6366F1") // indigo-500
			// Only 4K processing
			status4k == 3 -> "4K PROCESSING" to Color.parseColor("#6366F1") // indigo-500
			// Only HD processing
			status == 3 -> "HD PROCESSING" to Color.parseColor("#6366F1") // indigo-500
			
			// Both HD and 4K pending
			status == 2 && status4k == 2 -> "HD + 4K PENDING" to Color.parseColor("#EAB308") // yellow-500
			// Only 4K pending
			status4k == 2 -> "4K PENDING" to Color.parseColor("#EAB308") // yellow-500
			// Only HD pending
			status == 2 -> "HD PENDING" to Color.parseColor("#EAB308") // yellow-500
			
			// Both HD and 4K unknown
			status == 1 && status4k == 1 -> "HD + 4K UNKNOWN" to Color.parseColor("#9CA3AF") // gray-400
			// Only 4K unknown
			status4k == 1 -> "4K UNKNOWN" to Color.parseColor("#9CA3AF") // gray-400
			// Only HD unknown
			status == 1 -> "HD UNKNOWN" to Color.parseColor("#9CA3AF") // gray-400
			
			// Not requested
			else -> "NOT REQUESTED" to Color.parseColor("#6B7280") // gray-500
		}
		
		val badge = TextView(requireContext()).apply {
			text = statusText
			textSize = 10f  // Reduced from 12f
			setTextColor(Color.WHITE)
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			setPadding(16.dp(context), 6.dp(context), 16.dp(context), 6.dp(context))
			
			// Create pill-shaped background
			background = android.graphics.drawable.GradientDrawable().apply {
				setColor(bgColor)
				cornerRadius = 100.dp(context).toFloat()  // Large radius for pill shape
			}
			
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
		}
		
		return badge
	}

	/**
	 * Creates attributes line (certification, runtime, genres)
	 * Mimics: .media-attributes from Seerr
	 */
	private fun createAttributesSection(): View {
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 8.dp(context)
			}
		}

		val attributes = mutableListOf<String>()
		
		movieDetails?.runtime?.let { runtime ->
			attributes.add("$runtime min")
		}
		
		val genres = movieDetails?.genres?.take(3)?.map { it.name }
			?: tvDetails?.genres?.take(3)?.map { it.name }
			?: emptyList()
		attributes.addAll(genres)

		val attributesText = TextView(requireContext()).apply {
			text = attributes.joinToString(" • ")
			textSize = 14f
			setTextColor(Color.parseColor("#D1D5DB")) // gray-300
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
		}
		
		container.addView(attributesText)
		
		// Add tagline below genres if available
		val tagline = movieDetails?.tagline?.takeIf { it.isNotEmpty() } ?: tvDetails?.tagline?.takeIf { it.isNotEmpty() }
		if (tagline != null) {
			val taglineText = TextView(requireContext()).apply {
				text = "\"$tagline\""
				textSize = 14f
				setTextColor(Color.parseColor("#9CA3AF")) // gray-400
				setTypeface(typeface, android.graphics.Typeface.ITALIC)
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				).apply {
					topMargin = 4.dp(context)
				}
			}
			container.addView(taglineText)
		}
		
		return container
	}

	private fun createActionButtonsSection(): View {
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = android.view.Gravity.START
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 24.dp(context)
			}
		}

		val buttonTopMargin = 0

		// Check request status
		// Get mediaInfo from full details if available, otherwise from selectedItem
		val mediaInfo = movieDetails?.mediaInfo ?: tvDetails?.mediaInfo ?: selectedItem?.mediaInfo
		val hdStatus = mediaInfo?.status
		val status4k = mediaInfo?.status4k
		
		// Check for declined requests
		val requests = mediaInfo?.requests
		val hdDeclined = requests?.any { !it.is4k && it.status == 3 } == true
		val fourKDeclined = requests?.any { it.is4k && it.status == 3 } == true
		
		// HD Button: Disable if pending (2), processing (3), or fully available (5), or declined
		// Allow re-request if not requested (null/1), or partially available (4) so users can request missing episodes
		val isHdActive = (hdStatus != null && hdStatus >= 2 && hdStatus != 4) || hdDeclined
		val hdLabel = when {
			hdDeclined -> "HD Declined"
			hdStatus == 2 -> "HD Pending"
			hdStatus == 3 -> "HD Processing"
			hdStatus == 4 -> "Request More (HD)"
			hdStatus == 5 -> "HD Available"
			else -> "Request HD"
		}
		
		// 4K Button: Same logic for 4K
		val is4kActive = (status4k != null && status4k >= 2 && status4k != 4) || fourKDeclined
		val label4k = when {
			fourKDeclined -> "4K Declined"
			status4k == 2 -> "4K Pending"
			status4k == 3 -> "4K Processing"
			status4k == 4 -> "Request More (4K)"
			status4k == 5 -> "4K Available"
			else -> "Request 4K"
		}

		// Request button
		requestButton = TextUnderButton(requireContext()).apply {
			setLabel(hdLabel)
			setIcon(R.drawable.ic_select_quality)
			isEnabled = !isHdActive
			isFocusable = !isHdActive
			isFocusableInTouchMode = !isHdActive
			alpha = if (isHdActive) 0.5f else 1.0f
			setOnClickListener {
				if (!isHdActive) {
					requestContent(false)
				}
			}
			id = View.generateViewId()
			nextFocusLeftId = id
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				marginEnd = 8.dp(context)
				topMargin = buttonTopMargin
			}
		}
		container.addView(requestButton)

		// Request 4K button
		request4kButton = TextUnderButton(requireContext()).apply {
			setLabel(label4k)
			setIcon(R.drawable.ic_4k)
			isEnabled = !is4kActive
			isFocusable = !is4kActive
			isFocusableInTouchMode = !is4kActive
			alpha = if (is4kActive) 0.5f else 1.0f
			setOnClickListener {
				if (!is4kActive) {
					requestContent(true)
				}
			}
			id = View.generateViewId()
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				marginEnd = 8.dp(context)
				topMargin = buttonTopMargin
			}
		}
		container.addView(request4kButton)

		// Watch Trailer button
		trailerButton = TextUnderButton(requireContext()).apply {
			setLabel("Watch Trailer")
			setIcon(R.drawable.ic_trailer)
			setOnClickListener {
				playTrailer()
			}
			id = View.generateViewId()
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				marginEnd = 8.dp(context)
				topMargin = buttonTopMargin
			}
		}
		container.addView(trailerButton)
		
		// Play in Moonfin button (only show if available in library)
		if (hdStatus == 5 || hdStatus == 4) {
			playInMoonfinButton = TextUnderButton(requireContext()).apply {
				setLabel("Play in Moonfin")
				setIcon(R.drawable.ic_play)
				setOnClickListener {
					playInMoonfin()
				}
				id = View.generateViewId()
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				).apply {
					topMargin = buttonTopMargin
				}
			}
			container.addView(playInMoonfinButton)
		}
		
		return container
	}

	private fun createOverviewSection(): View {
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.HORIZONTAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			setPadding(24.dp(context), 0, 24.dp(context), 24.dp(context)) // No top padding to touch headerWrapper
			gravity = Gravity.TOP
		}

		// Left side - Overview text group (with top margin to move it down)
		val leftContainer = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				0,
				LinearLayout.LayoutParams.WRAP_CONTENT,
				2f // 2/3 width
			).apply {
				marginEnd = 32.dp(context)
			}
		}

		// Create a wrapper for tagline + overview with top margin to move down by 33%
		val overviewTextGroup = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = (100.dp(context) * 0.33).toInt() // Move down by 33% (raised 7% from 40%)
			}
		}

		// Overview heading
		val overviewHeading = TextView(requireContext()).apply {
			text = "Overview"
			textSize = 20f
			setTextColor(Color.parseColor("#D1D5DB")) // gray-300
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = 13.dp(context)
			}
		}
		overviewTextGroup.addView(overviewHeading)

		// Overview text
		val overview = movieDetails?.overview ?: tvDetails?.overview ?: selectedItem?.overview
		val overviewText = TextView(requireContext()).apply {
			text = overview?.ifEmpty { "Overview unavailable." } ?: "Overview unavailable."
			textSize = 14f
			setTextColor(Color.parseColor("#9CA3AF")) // gray-400
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
		}
		overviewTextGroup.addView(overviewText)
		
		// Add action buttons below overview
		overviewTextGroup.addView(createActionButtonsSection())

		leftContainer.addView(overviewTextGroup)
		container.addView(leftContainer)

		// Right side - Media facts
		container.addView(createMediaFactsSection())
		
		return container
	}

	/**
	 * Creates the media facts sidebar
	 * Mimics: .media-facts from Seerr
	 */
	private fun createMediaFactsSection(): View {
		Timber.d("MediaDetailsFragment: Creating metadata - movieDetails: ${movieDetails != null}, tvDetails: ${tvDetails != null}, status: ${movieDetails?.status ?: tvDetails?.status}, tagline: ${movieDetails?.tagline ?: tvDetails?.tagline}")
		
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				320.dp(context), // w-80 = 20rem = 320px
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = (100.dp(context) * 0.05).toInt() // Lower by 5%
			}
			setBackgroundColor(Color.TRANSPARENT) // Transparent background
			setPadding(0, 0, 0, 0)
		}

		// Collect all fact rows first
		val factRows = mutableListOf<Pair<String, String>>()
		
		// Add rating if available
		val voteAverage = movieDetails?.voteAverage ?: tvDetails?.voteAverage ?: selectedItem?.voteAverage
		if (voteAverage != null && voteAverage > 0) {
			factRows.add("TMDB Score" to "${(voteAverage * 10).toInt()}%")
		}

		// Status
		val status = movieDetails?.status ?: tvDetails?.status
		if (status != null) {
			factRows.add("Status" to status)
		}

		// TV Show specific fields
		val currentTvDetails = tvDetails
		if (currentTvDetails != null) {
			// First Air Date
			currentTvDetails.firstAirDate?.let { date ->
				val formattedDate = formatDate(date)
				if (formattedDate != null) {
					factRows.add("First Air Date" to formattedDate)
				}
			}
			
			// Last Air Date (if available)
			currentTvDetails.lastAirDate?.let { date ->
				val formattedDate = formatDate(date)
				if (formattedDate != null) {
					factRows.add("Last Air Date" to formattedDate)
				}
			}
			
			// Number of Seasons
			currentTvDetails.numberOfSeasons?.let { seasons ->
				factRows.add("Seasons" to seasons.toString())
			}
		}

		// Movie specific fields
		val currentMovieDetails = movieDetails
		if (currentMovieDetails != null) {
			// Release Date
			currentMovieDetails.releaseDate?.let { date ->
				val formattedDate = formatDate(date)
				if (formattedDate != null) {
					factRows.add("Release Date" to formattedDate)
				}
			}
			
			// Revenue
			currentMovieDetails.revenue?.let { revenue ->
				if (revenue > 0) {
					val formattedRevenue = NumberFormat.getCurrencyInstance(Locale.US).format(revenue)
					factRows.add("Revenue" to formattedRevenue)
				}
			}
		}

		// Runtime
		movieDetails?.runtime?.let { runtime ->
			val hours = runtime / 60
			val minutes = runtime % 60
			val runtimeText = if (hours > 0) {
				getString(R.string.runtime_hours_minutes, hours, minutes)
			} else {
				getString(R.string.runtime_minutes, minutes)
			}
			factRows.add("Runtime" to runtimeText)
		}

		// Budget (Movies only)
		val currentMovieDetailsForBudget = movieDetails
		if (currentMovieDetailsForBudget != null) {
			currentMovieDetailsForBudget.budget?.let { budget ->
				if (budget > 0) {
					val formattedBudget = NumberFormat.getCurrencyInstance(Locale.US).format(budget)
					factRows.add("Budget" to formattedBudget)
				}
			}
		}

		// Networks (TV Shows only)
		val studios = tvDetails?.networks?.take(3)?.map { it.name }
		if (!studios.isNullOrEmpty()) {
			factRows.add("Networks" to studios.joinToString(", "))
		}
		
		// Add rows with appropriate corner radius
		factRows.forEachIndexed { index, (label, value) ->
			val isFirst = index == 0
			val isLast = index == factRows.size - 1
			container.addView(createFactRow(label, value, isFirst, isLast))
		}

		return container
	}

	/**
	 * Creates a single fact row
	 * Mimics: .media-fact from Seerr
	 */
	private fun createFactRow(label: String, value: String, isFirst: Boolean = false, isLast: Boolean = false): View {
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.HORIZONTAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			setPadding(16.dp(context), 8.dp(context), 16.dp(context), 8.dp(context))
			// Add border with selective rounded corners
			background = android.graphics.drawable.GradientDrawable().apply {
				setColor(Color.TRANSPARENT) // Transparent background
				setStroke(1.dp(context), Color.parseColor("#374151")) // gray-700 border
				
				// Set corner radii: [top-left, top-right, bottom-right, bottom-left]
				val radius = 8.dp(context).toFloat()
				val radii = floatArrayOf(
					if (isFirst) radius else 0f, if (isFirst) radius else 0f, // top-left
					if (isFirst) radius else 0f, if (isFirst) radius else 0f, // top-right
					if (isLast) radius else 0f, if (isLast) radius else 0f,   // bottom-right
					if (isLast) radius else 0f, if (isLast) radius else 0f    // bottom-left
				)
				cornerRadii = radii
			}
		}

		val labelText = TextView(requireContext()).apply {
			text = label
			textSize = 13f
			setTextColor(Color.parseColor("#D1D5DB")) // gray-300
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			layoutParams = LinearLayout.LayoutParams(
				0,
				LinearLayout.LayoutParams.WRAP_CONTENT,
				1f
			)
		}
		container.addView(labelText)

		val valueText = TextView(requireContext()).apply {
			text = value
			textSize = 13f
			setTextColor(Color.parseColor("#9CA3AF")) // gray-400
			gravity = Gravity.END
			layoutParams = LinearLayout.LayoutParams(
				0,
				LinearLayout.LayoutParams.WRAP_CONTENT,
				1f
			)
		}
		container.addView(valueText)

		return container
	}

	private fun createCastSection(): View {
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = (-24.dp(context) * 0.7).toInt() // Raised by 30% (from 0.4 to 0.7)
			}
			setPadding(24.dp(context), 0, 24.dp(context), 24.dp(context))
			id = View.generateViewId()
		}
		
		castSection = container

		val castHeading = TextView(requireContext()).apply {
			text = "Cast"
			textSize = 24f
			setTextColor(Color.WHITE)
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = 16.dp(context)
			}
		}
		container.addView(castHeading)

		val castList = movieDetails?.credits?.cast?.take(20)
			?: tvDetails?.credits?.cast?.take(20)
		
		Timber.d("MediaDetailsFragment: Creating cast section - castList size: ${castList?.size}, movieDetails: ${movieDetails != null}, tvDetails: ${tvDetails != null}")
		
		if (!castList.isNullOrEmpty()) {
			val horizontalScrollView = android.widget.HorizontalScrollView(requireContext()).apply {
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				)
				isHorizontalScrollBarEnabled = false
			}
			
			val castRow = LinearLayout(requireContext()).apply {
				orientation = LinearLayout.HORIZONTAL
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				)
			}
			
			castList.forEach { cast ->
				val castCard = createCastCard(cast.id, cast.name ?: "Unknown", cast.character ?: "Unknown", cast.profilePath)
				castRow.addView(castCard)
			}
			
			horizontalScrollView.addView(castRow)
			container.addView(horizontalScrollView)
			
			requestButton?.nextFocusDownId = castRow.getChildAt(0)?.id ?: container.id
		} else {
			val noCast = TextView(requireContext()).apply {
				text = "Cast information not available"
				textSize = 14f
				setTextColor(Color.parseColor("#9CA3AF")) // gray-400
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				)
			}
			container.addView(noCast)
		}

		return container
	}
	
	private fun createCastCard(personId: Int, name: String, character: String, profilePath: String?): View {
		val cardWidth = 130.dp(requireContext())
		val imageSize = 100.dp(requireContext())
		
		val card = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
				rightMargin = 12.dp(context)
			}
			gravity = Gravity.CENTER_HORIZONTAL
			isFocusable = true
			isFocusableInTouchMode = true
			setPadding(8.dp(context), 8.dp(context), 8.dp(context), 8.dp(context))
			
			setOnFocusChangeListener { view, hasFocus ->
				if (hasFocus) {
					view.scaleX = 1.1f
					view.scaleY = 1.1f
					view.setBackgroundColor(Color.parseColor("#374151")) // gray-700
				} else {
					view.scaleX = 1.0f
					view.scaleY = 1.0f
					view.setBackgroundColor(Color.TRANSPARENT)
				}
			}

		setOnClickListener {
			// Navigate to person details
			navigationRepository.navigate(Destinations.jellyseerrPersonDetails(personId))
		}
	}

	val imageContainer = FrameLayout(requireContext()).apply {
			layoutParams = LinearLayout.LayoutParams(imageSize, imageSize).apply {
				gravity = Gravity.CENTER_HORIZONTAL
				bottomMargin = 8.dp(context)
			}
		}
		
		val profileImage = ImageView(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(imageSize, imageSize)
			scaleType = ImageView.ScaleType.CENTER_CROP
			setBackgroundColor(Color.parseColor("#1F2937"))
			
			clipToOutline = true
			outlineProvider = object : ViewOutlineProvider() {
				override fun getOutline(view: View, outline: Outline) {
					outline.setOval(0, 0, view.width, view.height)
				}
			}
		}
		
		if (!profilePath.isNullOrEmpty()) {
			val imageUrl = "https://image.tmdb.org/t/p/w185$profilePath"
			profileImage.load(imageUrl)
		}
		
		imageContainer.addView(profileImage)
		card.addView(imageContainer)
		
		val nameText = TextView(requireContext()).apply {
			text = name
			textSize = 14f
			setTextColor(Color.WHITE)
			maxLines = 2
			gravity = Gravity.CENTER
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = 4.dp(context)
			}
		}
		card.addView(nameText)
		
		val characterText = TextView(requireContext()).apply {
			text = character
			textSize = 12f
			setTextColor(Color.parseColor("#9CA3AF")) // gray-400
			maxLines = 2
			gravity = Gravity.CENTER
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
		}
		card.addView(characterText)
		
		return card
	}

	private fun createSimilarSection(): View {
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 32.dp(context)
			}
		}

		val similarHeading = TextView(requireContext()).apply {
			text = "Similar"
			textSize = 22f
			setTextColor(Color.WHITE)
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = 16.dp(context)
			}
		}
		container.addView(similarHeading)

		// Load similar content
		lifecycleScope.launch {
			try {
				val similarResult = when {
					movieDetails != null -> viewModel.getSimilarMovies(selectedItem!!.id)
					tvDetails != null -> viewModel.getSimilarTv(selectedItem!!.id)
					else -> null
				}

				similarResult?.getOrNull()?.let { similarPage ->
					val similarList = similarPage.results?.take(20) ?: emptyList()

				if (similarList.isNotEmpty()) {
					val scrollView = android.widget.HorizontalScrollView(requireContext()).apply {
						layoutParams = LinearLayout.LayoutParams(
							LinearLayout.LayoutParams.MATCH_PARENT,
							LinearLayout.LayoutParams.WRAP_CONTENT
						)
						isHorizontalScrollBarEnabled = false
						setPadding(12.dp(context), 0, 0, 0)
					}

					val similarRow = LinearLayout(requireContext()).apply {
						orientation = LinearLayout.HORIZONTAL
						layoutParams = LinearLayout.LayoutParams(
							LinearLayout.LayoutParams.WRAP_CONTENT,
							LinearLayout.LayoutParams.WRAP_CONTENT
						)
					}

					similarList.forEach { item ->
						val posterCard = createPosterCard(item)
						similarRow.addView(posterCard)
					}

					scrollView.addView(similarRow)
					container.addView(scrollView)
				} else {
						val noSimilar = TextView(requireContext()).apply {
							text = "No similar titles found"
							textSize = 14f
							setTextColor(Color.parseColor("#9CA3AF"))
							layoutParams = LinearLayout.LayoutParams(
								LinearLayout.LayoutParams.MATCH_PARENT,
								LinearLayout.LayoutParams.WRAP_CONTENT
							)
						}
						container.addView(noSimilar)
					}
				}
			} catch (e: Exception) {
				Timber.e(e, "Failed to load similar content")
			}
		}

		return container
	}

	private fun createPosterCard(item: JellyseerrDiscoverItemDto): View {
		val cardWidth = 150.dp(requireContext())
		val imageHeight = 225.dp(requireContext())

		val card = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
				marginEnd = 16.dp(context)
			}
			setPadding(0, 0, 0, 16.dp(context))
			isFocusable = true
			isFocusableInTouchMode = true

			setOnFocusChangeListener { view, hasFocus ->
				if (hasFocus) {
					view.scaleX = 1.05f
					view.scaleY = 1.05f
				} else {
					view.scaleX = 1.0f
					view.scaleY = 1.0f
				}
			}

			setOnClickListener {
				// Navigate to the details of this similar item
				val itemJson = Json.encodeToString(JellyseerrDiscoverItemDto.serializer(), item)
				navigationRepository.navigate(Destinations.jellyseerrMediaDetails(itemJson))
			}
		}

		val imageContainer = FrameLayout(requireContext()).apply {
			layoutParams = LinearLayout.LayoutParams(cardWidth, imageHeight).apply {
				bottomMargin = 8.dp(context)
			}
		}

		val posterImage = ImageView(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT
			)
			scaleType = ImageView.ScaleType.CENTER_CROP
			setBackgroundColor(Color.parseColor("#1F2937"))

			item.posterPath?.let { path ->
				val imageUrl = "https://image.tmdb.org/t/p/w500$path"
				load(imageUrl)
			}
		}
		imageContainer.addView(posterImage)
		card.addView(imageContainer)

		val titleText = TextView(requireContext()).apply {
			text = item.title ?: item.name ?: "Unknown"
			textSize = 14f
			setTextColor(Color.WHITE)
			maxLines = 2
			ellipsize = android.text.TextUtils.TruncateAt.END
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
		}
		card.addView(titleText)

		return card
	}

	private fun formatDate(dateString: String): String? {
		return try {
			val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
			val outputFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
			val date = inputFormat.parse(dateString)
			date?.let { outputFormat.format(it) }
		} catch (e: Exception) {
			dateString
		}
	}

	private fun requestContent(is4k: Boolean = false) {
		val item = selectedItem ?: return

		// If it's a TV show, show season selection dialog
		if (item.mediaType == "tv") {
			val numberOfSeasons = tvDetails?.numberOfSeasons ?: 1
			val showName = tvDetails?.name ?: item.name ?: item.title ?: "Unknown Show"
			
			val dialog = SeasonSelectionDialog(
				requireContext(),
				showName,
				numberOfSeasons,
				is4k
			) { selectedSeasons ->
				// Submit request with selected seasons
				submitRequest(item, selectedSeasons, is4k)
			}
			dialog.show()
		} else {
			// For movies, request directly
			submitRequest(item, null, is4k)
		}
	}
	
	private fun submitRequest(
		item: JellyseerrDiscoverItemDto,
		seasons: List<Int>?,
		is4k: Boolean
	) {
		lifecycleScope.launch {
			try {
				val result = viewModel.requestMedia(item, seasons, is4k)
				
				// Check if fragment is still attached before accessing context
				if (!isAdded) return@launch
				
				result.onSuccess {
					val quality = if (is4k) "4K" else "HD"
					val seasonInfo = if (seasons != null) {
						if (seasons.size == tvDetails?.numberOfSeasons) " (All seasons)"
						else " (${seasons.size} season${if (seasons.size > 1) "s" else ""})"
					} else ""
					Toast.makeText(
						requireContext(),
						"$quality request$seasonInfo submitted successfully!",
						Toast.LENGTH_SHORT
					).show()
					// Refresh details to update status
					loadFullDetails()
				}.onFailure { error ->
					Toast.makeText(
						requireContext(),
						"Failed to request: ${error.message}",
						Toast.LENGTH_LONG
					).show()
				}
			} catch (e: Exception) {
				Timber.e(e, "Request failed")
				// Check if fragment is still attached before showing toast
				if (isAdded) {
					Toast.makeText(
						requireContext(),
						"Request failed: ${e.message}",
						Toast.LENGTH_LONG
					).show()
				}
			}
		}
	}

	private fun playTrailer() {
		val item = selectedItem ?: return
		
		// Open YouTube search for the trailer
		// Format: "[Movie/Show Name] [Year] official trailer"
		val year = when {
			item.mediaType == "movie" -> item.releaseDate?.take(4)
			else -> item.firstAirDate?.take(4)
		}
		val title = item.title ?: item.name ?: "Unknown"
		val searchQuery = "$title ${year ?: ""} official trailer"
		
		try {
			// Try YouTube app first
			val youtubeIntent = android.content.Intent(
				android.content.Intent.ACTION_SEARCH
			).apply {
				setPackage("com.google.android.youtube.tv")
				putExtra("query", searchQuery)
			}
			
			if (youtubeIntent.resolveActivity(requireContext().packageManager) != null) {
				startActivity(youtubeIntent)
			} else {
				// Fallback to browser
				val browserIntent = android.content.Intent(
					android.content.Intent.ACTION_VIEW,
					android.net.Uri.parse("https://www.youtube.com/results?search_query=${android.net.Uri.encode(searchQuery)}")
				)
				startActivity(browserIntent)
			}
		} catch (e: Exception) {
			Timber.e(e, "Error opening trailer")
			Toast.makeText(requireContext(), "Unable to open trailer", Toast.LENGTH_SHORT).show()
		}
	}
	
	private fun playInMoonfin() {
		lifecycleScope.launch {
			try {
				// Get external IDs from movie or TV details
				val externalIds = movieDetails?.externalIds ?: tvDetails?.externalIds
				val tmdbId = externalIds?.tmdbId
				val tvdbId = externalIds?.tvdbId
				val imdbId = externalIds?.imdbId
				val title = movieDetails?.title ?: tvDetails?.name ?: tvDetails?.title ?: selectedItem?.title ?: selectedItem?.name
				val mediaType = movieDetails?.mediaType ?: tvDetails?.mediaType ?: selectedItem?.mediaType
				
				Timber.d("Searching for item in Jellyfin library - Title: $title, Type: $mediaType, TMDB: $tmdbId, TVDB: $tvdbId, IMDB: $imdbId")
				
				// Search for the item in Jellyfin library using provider IDs
				val jellyfinItem = searchForItemByProviderIds(
					tmdbId = tmdbId,
					tvdbId = tvdbId,
					imdbId = imdbId,
					title = title,
					mediaType = mediaType
				)
				
				if (jellyfinItem != null) {
					Timber.d("Found item in Jellyfin library: ${jellyfinItem.name} (${jellyfinItem.id})")
					// Navigate to Moonfin details page
					navigationRepository.navigate(Destinations.itemDetails(jellyfinItem.id))
				} else {
					Timber.w("Item not found in Jellyfin library")
					Toast.makeText(requireContext(), "Item not found in your Moonfin library", Toast.LENGTH_SHORT).show()
				}
			} catch (e: Exception) {
				Timber.e(e, "Failed to search for item in Moonfin")
				Toast.makeText(requireContext(), "Error searching library", Toast.LENGTH_SHORT).show()
			}
		}
	}
	
	/**
	 * Search for an item in Jellyfin library by provider IDs (TMDB, TVDB, IMDB)
	 * Falls back to title search if no provider ID matches found
	 */
	private suspend fun searchForItemByProviderIds(
		tmdbId: Int?,
		tvdbId: Int?,
		imdbId: String?,
		title: String?,
		mediaType: String?
	): BaseItemDto? = withContext(Dispatchers.IO) {
		try {
			// First try to search by title to get potential matches
			if (title == null) {
				Timber.w("No title available for search")
				return@withContext null
			}
			
			// Determine the correct Jellyfin item type based on Jellyseerr media type
			val includeItemTypes = when (mediaType) {
				"movie" -> setOf(BaseItemKind.MOVIE)
				"tv" -> setOf(BaseItemKind.SERIES)
				else -> setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES) // Search both if unknown
			}
			
			val response by apiClient.itemsApi.getItems(
				searchTerm = title,
				includeItemTypes = includeItemTypes,
				recursive = true,
				limit = 50 // Get more results to check provider IDs
			)
			
			Timber.d("Found ${response.items.size} items of type $mediaType matching title '$title'")
			
			// Try to match by provider IDs first (most accurate)
			if (tmdbId != null) {
				val tmdbMatch = response.items.firstOrNull { item ->
					val itemTmdbId = item.providerIds?.get("Tmdb")
					itemTmdbId != null && itemTmdbId == tmdbId.toString()
				}
				if (tmdbMatch != null) {
					Timber.d("Matched by TMDB ID: ${tmdbMatch.name} (${tmdbMatch.id})")
					return@withContext tmdbMatch
				}
			}
			
			if (tvdbId != null) {
				val tvdbMatch = response.items.firstOrNull { item ->
					val itemTvdbId = item.providerIds?.get("Tvdb")
					itemTvdbId != null && itemTvdbId == tvdbId.toString()
				}
				if (tvdbMatch != null) {
					Timber.d("Matched by TVDB ID: ${tvdbMatch.name} (${tvdbMatch.id})")
					return@withContext tvdbMatch
				}
			}
			
			if (imdbId != null) {
				val imdbMatch = response.items.firstOrNull { item ->
					val itemImdbId = item.providerIds?.get("Imdb")
					itemImdbId != null && itemImdbId == imdbId
				}
				if (imdbMatch != null) {
					Timber.d("Matched by IMDB ID: ${imdbMatch.name} (${imdbMatch.id})")
					return@withContext imdbMatch
				}
			}
			
			// Fallback to exact title match
			val exactMatch = response.items.firstOrNull { item ->
				item.name.equals(title, ignoreCase = true)
			}
			
			if (exactMatch != null) {
				Timber.d("Matched by exact title: ${exactMatch.name} (${exactMatch.id})")
				return@withContext exactMatch
			}
			
			// Last resort: return first result if it's a close match
			val firstResult = response.items.firstOrNull()
			if (firstResult != null) {
				Timber.w("Using first search result as fallback: ${firstResult.name} (${firstResult.id})")
			}
			
			firstResult
		} catch (e: Exception) {
			Timber.e(e, "Failed to search Jellyfin library")
			null
		}
	}
}
