package org.jellyfin.androidtv.ui.jellyseerr

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
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
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.jellyfin.androidtv.util.toHtmlSpanned
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrMovieDetailsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrRequestDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrTvDetailsDto
import org.jellyfin.androidtv.ui.itemhandling.JellyseerrMediaBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.JellyseerrPersonBaseRowItem
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.TextUnderButton
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.NavbarPosition
import org.jellyfin.androidtv.ui.shared.toolbar.LeftSidebarNavigation
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
	private val userPreferences: UserPreferences by inject()
	
	private var selectedItem: JellyseerrDiscoverItemDto? = null
	private var movieDetails: JellyseerrMovieDetailsDto? = null
	private var tvDetails: JellyseerrTvDetailsDto? = null
	private var requestButton: View? = null
	private var cancelRequestButton: View? = null
	private var trailerButton: View? = null
	private var playInMoonfinButton: View? = null
	private var castSection: View? = null
	private var toolbarContainer: View? = null
	private var sidebarId: Int = View.NO_ID

	// Items per section (cast/recommendations/similar)
	private val ITEMS_PER_SECTION = 45  // 3 pages * ~15 items per page

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
		sidebarId = View.generateViewId()
		
		val mainContainer = object : FrameLayout(requireContext()) {
			override fun dispatchKeyEvent(event: KeyEvent): Boolean {
				if (event.action == KeyEvent.ACTION_DOWN) {
					when (event.keyCode) {
						KeyEvent.KEYCODE_DPAD_LEFT -> {
							val focused = findFocus()
							if (focused != null && isAtLeftEdge(focused)) {
								val sidebar = findViewById<View>(sidebarId)
								if (sidebar != null && sidebar.isVisible) {
									sidebar.requestFocus()
									return true
								}
							}
						}
					}
				}
				return super.dispatchKeyEvent(event)
			}
		}.apply {
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			)
			setBackgroundColor(Color.parseColor("#111827"))
		}

		val navbarPosition = userPreferences[UserPreferences.navbarPosition]

		val scrollView = ScrollView(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT
			)
			setBackgroundColor(Color.parseColor("#111827"))
			isFocusable = true
			isFocusableInTouchMode = true
			isScrollbarFadingEnabled = false
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
		
		when (navbarPosition) {
			NavbarPosition.LEFT -> {
				val sidebarContainer = FrameLayout(requireContext()).apply {
					layoutParams = FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.WRAP_CONTENT,
						FrameLayout.LayoutParams.MATCH_PARENT
					).apply {
						gravity = Gravity.START
					}
					elevation = 8f * resources.displayMetrics.density
				}
				
				val sidebarOverlay = ComposeView(requireContext()).apply {
					id = sidebarId
					layoutParams = FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.WRAP_CONTENT,
						FrameLayout.LayoutParams.MATCH_PARENT
					)
					setContent {
						LeftSidebarNavigation(
							activeButton = MainToolbarActiveButton.Jellyseerr
						)
					}
				}
				toolbarContainer = sidebarOverlay
				sidebarContainer.addView(sidebarOverlay)
				mainContainer.addView(sidebarContainer)
			}
			NavbarPosition.TOP -> {
				val topToolbarContainer = FrameLayout(requireContext()).apply {
					layoutParams = FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.WRAP_CONTENT
					).apply {
						gravity = Gravity.TOP
					}
					elevation = 8f * resources.displayMetrics.density
				}
				
				val topToolbarOverlay = ComposeView(requireContext()).apply {
					id = sidebarId
					layoutParams = FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.WRAP_CONTENT
					)
					setContent {
						MainToolbar(
							activeButton = MainToolbarActiveButton.Jellyseerr
						)
					}
				}
				toolbarContainer = topToolbarOverlay
				topToolbarContainer.addView(topToolbarOverlay)
				mainContainer.addView(topToolbarContainer)
			}
		}
		
		return mainContainer
	}

	/**
	 * Check if a focused view is at the left edge of its scrollable parent.
	 * For views inside a HorizontalScrollView at scrollX == 0 and first child position,
	 * or for views that are the first focusable child in their row.
	 */
	private fun isAtLeftEdge(view: View): Boolean {
		val hsv = findParentOfType<android.widget.HorizontalScrollView>(view)
		if (hsv != null) {
			if (hsv.scrollX != 0) return false
			val rowContainer = hsv.getChildAt(0) as? ViewGroup ?: return true
			val firstChild = rowContainer.getChildAt(0) ?: return true
			return view === firstChild || isDescendantOf(view, firstChild)
		}
		// For non-scrolling containers (e.g. button row), check if this is the leftmost focusable
		val parent = view.parent as? ViewGroup ?: return true
		for (i in 0 until parent.childCount) {
			val child = parent.getChildAt(i)
			if (child.isFocusable) {
				return child === view
			}
		}
		return true
	}

	private fun isDescendantOf(view: View, ancestor: View): Boolean {
		var current: android.view.ViewParent? = view.parent
		while (current != null) {
			if (current === ancestor) return true
			current = current.parent
		}
		return false
	}

	private inline fun <reified T : View> findParentOfType(view: View): T? {
		var current = view.parent
		while (current != null) {
			if (current is T) return current
			current = current.parent
		}
		return null
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
				leftMargin = 50.dp(context)
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
			setPadding(50.dp(context), 0, 50.dp(context), 8.dp(context))
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
			setPadding(50.dp(context), 0, 50.dp(context), 0)
			clipChildren = false
			clipToPadding = false
		}
		contentWrapper.addView(createOverviewSection())
		contentWrapper.addView(createCastSection())
		contentWrapper.addView(createRecommendationsSection())
		contentWrapper.addView(createSimilarSection())
		contentWrapper.addView(createKeywordsSection())
		container.addView(contentWrapper)
		
		val titleWrapper = FrameLayout(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 230.dp(context)
				leftMargin = 270.dp(context)
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
		
		// Determine if HD/4K are requestable
		// Blocked if: pending (2), processing (3), available (5), blacklisted (6), or declined
		// Requestable if: not requested (null/1), or partially available (4)
		val isHdBlocked = (hdStatus != null && hdStatus >= 2 && hdStatus != 4) || hdDeclined
		val is4kBlocked = (status4k != null && status4k >= 2 && status4k != 4) || fourKDeclined
		
		// Determine button state and label
		val canRequestHd = !isHdBlocked
		val canRequest4k = !is4kBlocked
		val canRequestAny = canRequestHd || canRequest4k
		
		// Determine the button label based on status
		val requestLabel = when {
			!canRequestAny -> getStatusLabel(hdStatus, status4k, hdDeclined, fourKDeclined)
			hdStatus == 4 && status4k == 4 -> "Request More"
			hdStatus == 4 -> "Request More"
			status4k == 4 -> "Request More"
			else -> "Request"
		}

		// Single Request button
		requestButton = TextUnderButton(requireContext()).apply {
			setLabel(requestLabel)
			setIcon(R.drawable.ic_select_quality)
			isEnabled = canRequestAny
			isFocusable = canRequestAny
			isFocusableInTouchMode = canRequestAny
			alpha = if (canRequestAny) 1.0f else 0.5f
			setOnClickListener {
				if (canRequestAny) {
					handleRequestClick(canRequestHd, canRequest4k, hdStatus, status4k)
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
		container.addView(requestButton)

		// Cancel Request button - show if there are pending (status=1) requests
		val pendingRequests = requests?.filter { it.status == JellyseerrRequestDto.STATUS_PENDING } ?: emptyList()
		if (pendingRequests.isNotEmpty()) {
			cancelRequestButton = TextUnderButton(requireContext()).apply {
				setLabel("Cancel Request")
				setIcon(R.drawable.ic_delete)
				setOnClickListener {
					showCancelRequestDialog(pendingRequests)
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
			container.addView(cancelRequestButton)
		}

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
	
	/**
	 * Get a combined status label when nothing is requestable
	 */
	private fun getStatusLabel(hdStatus: Int?, status4k: Int?, hdDeclined: Boolean, fourKDeclined: Boolean): String {
		return when {
			hdDeclined && fourKDeclined -> "Declined"
			fourKDeclined -> "4K Declined"
			hdDeclined -> "HD Declined"
			hdStatus == 5 && status4k == 5 -> "Available"
			status4k == 5 -> "4K Available"
			hdStatus == 5 -> "HD Available"
			hdStatus == 3 && status4k == 3 -> "Processing"
			status4k == 3 -> "4K Processing"
			hdStatus == 3 -> "HD Processing"
			hdStatus == 2 && status4k == 2 -> "Pending"
			status4k == 2 -> "4K Pending"
			hdStatus == 2 -> "HD Pending"
			hdStatus == 6 || status4k == 6 -> "Blacklisted"
			else -> "Unavailable"
		}
	}
	
	/**
	 * Handle request button click - show quality selection if both options available
	 */
	private fun handleRequestClick(canRequestHd: Boolean, canRequest4k: Boolean, hdStatus: Int?, status4k: Int?) {
		val item = selectedItem ?: return
		val mediaType = item.mediaType ?: return
		val title = when (mediaType) {
			"movie" -> movieDetails?.title ?: item.title ?: item.name ?: "Unknown"
			else -> tvDetails?.name ?: item.name ?: item.title ?: "Unknown"
		}
		
		lifecycleScope.launch {
			val (userCan4k, has4kServer, hasHdServer) = checkQualityAvailability(mediaType)
			val hdAvailable = canRequestHd && hasHdServer
			val fourKAvailable = canRequest4k && userCan4k && has4kServer
			
			if (hdAvailable && fourKAvailable) {
				// Both available - show quality selection dialog
				val dialog = QualitySelectionDialog(
					requireContext(),
					title = title,
					canRequestHd = true,
					canRequest4k = true,
					hdStatus = hdStatus,
					status4k = status4k
				) { is4k ->
					requestContent(is4k)
				}
				dialog.show()
			} else if (fourKAvailable) {
				// Only 4K available
				requestContent(true)
			} else if (hdAvailable) {
				requestContent(false)
			} else {
				if (!isAdded) return@launch
				val mediaTypeName = if (mediaType == "movie") "movies" else "TV shows"
				Toast.makeText(
					requireContext(),
					"No Radarr/Sonarr server configured for $mediaTypeName in Jellyseerr",
					Toast.LENGTH_LONG
				).show()
			}
		}
	}
	
	/**
	 * Check if 4K requests are possible for the given media type
	 * Returns (userHas4kPermission, server4kAvailable, serverHdAvailable)
	 */
	private suspend fun checkQualityAvailability(mediaType: String): Triple<Boolean, Boolean, Boolean> {
		return try {
			// Check user permissions
			val userResult = viewModel.getCurrentUser()
			val user = userResult.getOrNull()
			val userCan4k = when (mediaType) {
				"movie" -> user?.canRequest4kMovies() ?: false
				"tv" -> user?.canRequest4kTv() ?: false
				else -> user?.canRequest4k() ?: false
			}
			
			val (has4kServer, hasHdServer) = when (mediaType) {
				"movie" -> {
					val radarrResult = viewModel.getRadarrServers()
					val servers = radarrResult.getOrNull() ?: emptyList()
					Pair(servers.any { it.is4k }, servers.any { !it.is4k })
				}
				"tv" -> {
					val sonarrResult = viewModel.getSonarrServers()
					val servers = sonarrResult.getOrNull() ?: emptyList()
					Pair(servers.any { it.is4k }, servers.any { !it.is4k })
				}
				else -> Pair(false, false)
			}
			
			Triple(userCan4k, has4kServer, hasHdServer)
		} catch (e: Exception) {
			Timber.e(e, "Failed to check quality availability")
			// Default to allowing both if check fails (let server reject if not allowed)
			Triple(true, true, true)
		}
	}

	private fun createOverviewSection(): View {
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.HORIZONTAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			setPadding(16.dp(context), 0, 24.dp(context), 24.dp(context)) // No top padding to touch headerWrapper
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

		val overview = movieDetails?.overview ?: tvDetails?.overview ?: selectedItem?.overview
		val overviewText = TextView(requireContext()).apply {
			val htmlText = overview?.toHtmlSpanned()
			text = if (htmlText?.isNotEmpty() == true) htmlText else "Overview unavailable."
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
				topMargin = (100.dp(context) * 0.33).toInt()
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
				topMargin = (-24.dp(context) * 0.7).toInt()
			}
			setPadding(24.dp(context), 0, 24.dp(context), 24.dp(context))
			clipChildren = false
			clipToPadding = false
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

		val castList = movieDetails?.credits?.cast?.take(ITEMS_PER_SECTION)
			?: tvDetails?.credits?.cast?.take(ITEMS_PER_SECTION)
		
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
				clipChildren = false
				clipToPadding = false
			}
			
			val castPresenter = CardPresenter(true, 130)
			castList.forEach { cast ->
				val rowItem = JellyseerrPersonBaseRowItem(cast)
				val vh = castPresenter.onCreateViewHolder(castRow)
				castPresenter.onBindViewHolder(vh, rowItem)
				vh.view.apply {
					setOnClickListener {
						navigationRepository.navigate(Destinations.jellyseerrPersonDetails(cast.id))
					}
					val lp = layoutParams as? ViewGroup.MarginLayoutParams
					if (lp != null) {
						lp.marginEnd = 12.dp(context)
					} else {
						layoutParams = LinearLayout.LayoutParams(
							LinearLayout.LayoutParams.WRAP_CONTENT,
							LinearLayout.LayoutParams.WRAP_CONTENT
						).apply { marginEnd = 12.dp(context) }
					}
				}
				castRow.addView(vh.view)
			}
			
			horizontalScrollView.addView(castRow)
			container.addView(horizontalScrollView)
			
			requestButton?.nextFocusDownId = castRow.getChildAt(0)?.id ?: container.id
		} else {
			val noCast = TextView(requireContext()).apply {
				text = "Cast information not available"
				textSize = 14f
				setTextColor(Color.parseColor("#9CA3AF"))
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				)
			}
			container.addView(noCast)
		}

		return container
	}
	
	private fun createRecommendationsSection(): View {
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 32.dp(context)
			}
			clipChildren = false
			clipToPadding = false
		}

		val recommendationsHeading = TextView(requireContext()).apply {
			text = "Recommendations"
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
		container.addView(recommendationsHeading)

		lifecycleScope.launch {
			try {
				val allRecommendations = mutableListOf<JellyseerrDiscoverItemDto>()
				for (page in 1..3) {
					val recommendationsResult = when {
						movieDetails != null -> viewModel.getRecommendationsMovies(selectedItem!!.id, page)
						tvDetails != null -> viewModel.getRecommendationsTv(selectedItem!!.id, page)
						else -> null
					}
					recommendationsResult?.getOrNull()?.let { pageResult ->
						allRecommendations.addAll(pageResult.results ?: emptyList())
					}
				}

				val recommendationsList = allRecommendations.take(ITEMS_PER_SECTION)

				if (recommendationsList.isNotEmpty()) {
					val scrollView = android.widget.HorizontalScrollView(requireContext()).apply {
						layoutParams = LinearLayout.LayoutParams(
							LinearLayout.LayoutParams.MATCH_PARENT,
							LinearLayout.LayoutParams.WRAP_CONTENT
						)
						isHorizontalScrollBarEnabled = false
						setPadding(12.dp(context), 0, 0, 0)
						clipChildren = false
						clipToPadding = false
					}

					val recommendationsRow = LinearLayout(requireContext()).apply {
						orientation = LinearLayout.HORIZONTAL
						layoutParams = LinearLayout.LayoutParams(
							LinearLayout.LayoutParams.WRAP_CONTENT,
							LinearLayout.LayoutParams.WRAP_CONTENT
						)
						clipChildren = false
						clipToPadding = false
					}

					val posterPresenter = CardPresenter()
					recommendationsList.forEach { item ->
						val rowItem = JellyseerrMediaBaseRowItem(item)
						val vh = posterPresenter.onCreateViewHolder(recommendationsRow)
						posterPresenter.onBindViewHolder(vh, rowItem)
						vh.view.apply {
							setOnClickListener {
								val itemJson = Json.encodeToString(JellyseerrDiscoverItemDto.serializer(), item)
								navigationRepository.navigate(Destinations.jellyseerrMediaDetails(itemJson))
							}
							val lp = layoutParams as? ViewGroup.MarginLayoutParams
							if (lp != null) {
								lp.marginEnd = 12.dp(context)
							} else {
								layoutParams = LinearLayout.LayoutParams(
									LinearLayout.LayoutParams.WRAP_CONTENT,
									LinearLayout.LayoutParams.WRAP_CONTENT
								).apply { marginEnd = 12.dp(context) }
							}
						}
						recommendationsRow.addView(vh.view)
					}

					scrollView.addView(recommendationsRow)
					container.addView(scrollView)
				} else {
					val noRecommendations = TextView(requireContext()).apply {
						text = "No recommendations found"
						textSize = 14f
						setTextColor(Color.parseColor("#9CA3AF"))
						layoutParams = LinearLayout.LayoutParams(
							LinearLayout.LayoutParams.MATCH_PARENT,
							LinearLayout.LayoutParams.WRAP_CONTENT
						)
					}
					container.addView(noRecommendations)
				}
			} catch (e: Exception) {
				Timber.e(e, "Failed to load recommendations")
			}
		}

		return container
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
			clipChildren = false
			clipToPadding = false
		}

		val similarTitle = when {
			tvDetails != null -> "Similar Series"
			else -> "Similar Titles"
		}

		val similarHeading = TextView(requireContext()).apply {
			text = similarTitle
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

		lifecycleScope.launch {
			try {
				val allSimilar = mutableListOf<JellyseerrDiscoverItemDto>()
				for (page in 1..3) {
					val similarResult = when {
						movieDetails != null -> viewModel.getSimilarMovies(selectedItem!!.id, page)
						tvDetails != null -> viewModel.getSimilarTv(selectedItem!!.id, page)
						else -> null
					}
					similarResult?.getOrNull()?.let { pageResult ->
						allSimilar.addAll(pageResult.results ?: emptyList())
					}
				}

				val similarList = allSimilar.take(ITEMS_PER_SECTION)

				if (similarList.isNotEmpty()) {
					val scrollView = android.widget.HorizontalScrollView(requireContext()).apply {
						layoutParams = LinearLayout.LayoutParams(
							LinearLayout.LayoutParams.MATCH_PARENT,
							LinearLayout.LayoutParams.WRAP_CONTENT
						)
						isHorizontalScrollBarEnabled = false
						setPadding(12.dp(context), 0, 0, 0)
						clipChildren = false
						clipToPadding = false
					}

					val similarRow = LinearLayout(requireContext()).apply {
						orientation = LinearLayout.HORIZONTAL
						layoutParams = LinearLayout.LayoutParams(
							LinearLayout.LayoutParams.WRAP_CONTENT,
							LinearLayout.LayoutParams.WRAP_CONTENT
						)
						clipChildren = false
						clipToPadding = false
					}

					val posterPresenter = CardPresenter()
					similarList.forEach { item ->
						val rowItem = JellyseerrMediaBaseRowItem(item)
						val vh = posterPresenter.onCreateViewHolder(similarRow)
						posterPresenter.onBindViewHolder(vh, rowItem)
						vh.view.apply {
							setOnClickListener {
								val itemJson = Json.encodeToString(JellyseerrDiscoverItemDto.serializer(), item)
								navigationRepository.navigate(Destinations.jellyseerrMediaDetails(itemJson))
							}
							val lp = layoutParams as? ViewGroup.MarginLayoutParams
							if (lp != null) {
								lp.marginEnd = 12.dp(context)
							} else {
								layoutParams = LinearLayout.LayoutParams(
									LinearLayout.LayoutParams.WRAP_CONTENT,
									LinearLayout.LayoutParams.WRAP_CONTENT
								).apply { marginEnd = 12.dp(context) }
							}
						}
						similarRow.addView(vh.view)
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
			} catch (e: Exception) {
				Timber.e(e, "Failed to load similar content")
			}
		}

		return container
	}

	private fun createKeywordsSection(): View {
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 32.dp(context)
			}
			clipChildren = false
			clipToPadding = false
		}

		// Get keywords from movie or TV details
		val keywords = when {
			movieDetails != null -> movieDetails?.keywords ?: emptyList()
			tvDetails != null -> tvDetails?.keywords ?: emptyList()
			else -> emptyList()
		}

		// Only show section if there are keywords
		if (keywords.isEmpty()) {
			return container
		}

		val keywordsHeading = TextView(requireContext()).apply {
			text = "Keywords"
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
		container.addView(keywordsHeading)

		// Create grid layout for keywords (FlexboxLayout alternative using LinearLayouts)
		val keywordsContainer = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			setPadding(0, 0, 0, 0)
			clipChildren = false
			clipToPadding = false
		}

		// Group keywords into rows - dynamic width wrapping
		var currentRow: LinearLayout? = null
		var itemsInRow = 0

		keywords.forEach { keyword ->
			if (currentRow == null) {
				currentRow = LinearLayout(requireContext()).apply {
					orientation = LinearLayout.HORIZONTAL
					layoutParams = LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT
					).apply {
						bottomMargin = 12.dp(context)
					}
					clipChildren = false
					clipToPadding = false
				}
				keywordsContainer.addView(currentRow)
				itemsInRow = 0
			}

			// Create pill-shaped background drawable
			val pillBackground = android.graphics.drawable.GradientDrawable().apply {
				shape = android.graphics.drawable.GradientDrawable.RECTANGLE
				cornerRadius = 100.dp(requireContext()).toFloat()
				setColor(Color.parseColor("#374151"))
			}

			val pillBackgroundFocused = android.graphics.drawable.GradientDrawable().apply {
				shape = android.graphics.drawable.GradientDrawable.RECTANGLE
				cornerRadius = 100.dp(requireContext()).toFloat()
				setColor(Color.parseColor("#4B5563"))
			}

			val keywordTag = TextView(requireContext()).apply {
				text = keyword.name
				textSize = 16f
				setTextColor(Color.WHITE)
				background = pillBackground
				setPadding(24.dp(context), 12.dp(context), 24.dp(context), 12.dp(context))
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				).apply {
					marginEnd = 12.dp(context)
				}
				isFocusable = true
				isFocusableInTouchMode = true

				setOnClickListener {
					// Navigate to browse-by with keyword filter
					val mediaType = if (movieDetails != null) "movie" else "tv"
					navigationRepository.navigate(
						Destinations.jellyseerrBrowseBy(
							filterId = keyword.id,
							filterName = keyword.name,
							mediaType = mediaType,
							filterType = BrowseFilterType.KEYWORD
						)
					)
				}

				setOnFocusChangeListener { view, hasFocus ->
					if (hasFocus) {
						background = pillBackgroundFocused
						view.scaleX = 1.05f
						view.scaleY = 1.05f
					} else {
						background = pillBackground
						view.scaleX = 1.0f
						view.scaleY = 1.0f
					}
				}
			}

			currentRow?.addView(keywordTag)
			itemsInRow++
			
			// Start new row if we've filled the width (this will wrap naturally with horizontal scroll if needed)
			// For now, we'll use a simple max items per row approach
			if (itemsInRow >= 5) {
				currentRow = null
			}
		}

		container.addView(keywordsContainer)
		return container
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

		// Check if user has advanced permission and should show options dialog
		lifecycleScope.launch {
			val userResult = viewModel.getCurrentUser()
			val user = userResult.getOrNull()
			val hasAdvanced = user?.hasAdvancedRequestPermission() ?: false
			
			if (hasAdvanced) {
				// Show advanced options dialog before proceeding
				showAdvancedOptionsDialog(item, is4k)
			} else {
				// No advanced permission, proceed directly
				proceedWithRequest(item, is4k, null)
			}
		}
	}
	
	/**
	 * Show advanced request options dialog for users with REQUEST_ADVANCED permission
	 */
	private fun showAdvancedOptionsDialog(item: JellyseerrDiscoverItemDto, is4k: Boolean) {
		val isMovie = item.mediaType == "movie"
		val title = when {
			isMovie -> movieDetails?.title ?: item.title ?: item.name ?: "Unknown"
			else -> tvDetails?.name ?: item.name ?: item.title ?: "Unknown"
		}
		
		lifecycleScope.launch {
			val serverExists = try {
				if (isMovie) {
					val serversResult = viewModel.getRadarrServers()
					serversResult.getOrNull()?.any { it.is4k == is4k } ?: false
				} else {
					val serversResult = viewModel.getSonarrServers()
					serversResult.getOrNull()?.any { it.is4k == is4k } ?: false
				}
			} catch (e: Exception) {
				Timber.e(e, "Failed to check server availability")
				false
			}
			
			if (!serverExists) {
				if (!isAdded) return@launch
				val quality = if (is4k) "4K" else "HD (1080p)"
				val mediaType = if (isMovie) "movies" else "TV shows"
				Toast.makeText(
					requireContext(),
					"No $quality server configured for $mediaType in Jellyseerr. Please contact your administrator.",
					Toast.LENGTH_LONG
				).show()
				return@launch
			}
			
			val dialog = AdvancedRequestOptionsDialog(
				context = requireContext(),
				title = title,
				is4k = is4k,
				isMovie = isMovie,
				coroutineScope = lifecycleScope,
				onLoadData = {
					loadServerDetailsForAdvancedOptions(isMovie, is4k)
				},
				onConfirm = { options ->
					proceedWithRequest(item, is4k, options)
				},
				onCancel = {
					// User cancelled, do nothing
				}
			)
			dialog.show()
		}
	}
	
	/**
	 * Load server details for the advanced options dialog
	 */
	private suspend fun loadServerDetailsForAdvancedOptions(
		isMovie: Boolean,
		is4k: Boolean
	): AdvancedRequestOptionsDialog.ServerDetailsData? {
		return try {
			if (isMovie) {
				val serversResult = viewModel.getRadarrServers()
				val servers = serversResult.getOrNull() ?: return null
				val server = servers.find { it.is4k == is4k } ?: run {
					Timber.w("No Radarr server configured for ${if (is4k) "4K" else "HD"} requests")
					return null
				}
				val detailsResult = viewModel.getRadarrServerDetails(server.id)
				val details = detailsResult.getOrNull() ?: return null
				
				AdvancedRequestOptionsDialog.ServerDetailsData(
					serverId = server.id,
					profiles = details.profiles,
					rootFolders = details.rootFolders,
					defaultProfileId = server.activeProfileId,
					defaultRootFolder = server.activeDirectory
				)
			} else {
				val serversResult = viewModel.getSonarrServers()
				val servers = serversResult.getOrNull() ?: return null
				val server = servers.find { it.is4k == is4k } ?: run {
					Timber.w("No Sonarr server configured for ${if (is4k) "4K" else "HD"} requests")
					return null
				}
				val detailsResult = viewModel.getSonarrServerDetails(server.id)
				val details = detailsResult.getOrNull() ?: return null
				
				AdvancedRequestOptionsDialog.ServerDetailsData(
					serverId = server.id,
					profiles = details.profiles,
					rootFolders = details.rootFolders,
					defaultProfileId = server.activeProfileId,
					defaultRootFolder = server.activeDirectory
				)
			}
		} catch (e: Exception) {
			Timber.e(e, "Failed to load server details for advanced options")
			null
		}
	}
	
	/**
	 * Proceed with the request after any dialogs
	 */
	private fun proceedWithRequest(
		item: JellyseerrDiscoverItemDto, 
		is4k: Boolean, 
		advancedOptions: AdvancedRequestOptions?
	) {
		// If it's a TV show, show season selection dialog
		if (item.mediaType == "tv") {
			val numberOfSeasons = tvDetails?.numberOfSeasons ?: 1
			val showName = tvDetails?.name ?: item.name ?: item.title ?: "Unknown Show"
			
			// Gather unavailable seasons (already requested or available for this quality)
			val unavailableSeasons = getUnavailableSeasons(is4k)
			
			val dialog = SeasonSelectionDialog(
				requireContext(),
				showName,
				numberOfSeasons,
				is4k,
				unavailableSeasons
			) { selectedSeasons ->
				// Submit request with selected seasons
				submitRequest(item, selectedSeasons, is4k, advancedOptions)
			}
			dialog.show()
		} else {
			// For movies, request directly
			submitRequest(item, null, is4k, advancedOptions)
		}
	}
	
	/**
	 * Get set of season numbers that are already requested or available
	 * for the specified quality (HD or 4K)
	 */
	private fun getUnavailableSeasons(is4k: Boolean): Set<Int> {
		val unavailableSeasons = mutableSetOf<Int>()
		val mediaInfo = tvDetails?.mediaInfo ?: return unavailableSeasons
		
		// Check existing requests for this quality
		mediaInfo.requests?.forEach { request ->
			// Only consider requests matching the quality (HD or 4K)
			if (request.is4k == is4k) {
				// Only consider non-declined requests
				if (request.status != JellyseerrRequestDto.STATUS_DECLINED) {
					// Add all seasons from this request
					request.seasons?.forEach { seasonRequest ->
						unavailableSeasons.add(seasonRequest.seasonNumber)
					}
				}
			}
		}
		
		return unavailableSeasons
	}
	
	private fun submitRequest(
		item: JellyseerrDiscoverItemDto,
		seasons: List<Int>?,
		is4k: Boolean,
		advancedOptions: AdvancedRequestOptions? = null
	) {
		lifecycleScope.launch {
			try {
				val result = viewModel.requestMedia(item, seasons, is4k, advancedOptions)
				
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

	/**
	 * Show confirmation dialog to cancel pending request(s)
	 */
	private fun showCancelRequestDialog(pendingRequests: List<JellyseerrRequestDto>) {
		if (pendingRequests.isEmpty()) return
		
		val item = selectedItem ?: return
		val title = when (item.mediaType) {
			"movie" -> movieDetails?.title ?: item.title ?: item.name ?: "Unknown"
			else -> tvDetails?.name ?: item.name ?: item.title ?: "Unknown"
		}
		
		// Build description of what will be cancelled
		val description = if (pendingRequests.size == 1) {
			val req = pendingRequests.first()
			val quality = if (req.is4k) "4K" else "HD"
			"Cancel $quality request for \"$title\"?"
		} else {
			val hdCount = pendingRequests.count { !it.is4k }
			val fourKCount = pendingRequests.count { it.is4k }
			val parts = mutableListOf<String>()
			if (hdCount > 0) parts.add("$hdCount HD")
			if (fourKCount > 0) parts.add("$fourKCount 4K")
			"Cancel ${parts.joinToString(" and ")} request${if (pendingRequests.size > 1) "s" else ""} for \"$title\"?"
		}
		
		android.app.AlertDialog.Builder(requireContext())
			.setTitle("Cancel Request")
			.setMessage(description)
			.setPositiveButton("Cancel Request") { _, _ ->
				cancelPendingRequests(pendingRequests)
			}
			.setNegativeButton("Keep Request", null)
			.show()
	}
	
	/**
	 * Cancel the given pending requests
	 */
	private fun cancelPendingRequests(requests: List<JellyseerrRequestDto>) {
		lifecycleScope.launch {
			try {
				var successCount = 0
				var failCount = 0
				
				for (request in requests) {
					val result = viewModel.cancelRequest(request.id)
					if (result.isSuccess) {
						successCount++
					} else {
						failCount++
						Timber.e(result.exceptionOrNull(), "Failed to cancel request ${request.id}")
					}
				}
				
				// Check if fragment is still attached
				if (!isAdded) return@launch
				
				val message = when {
					failCount == 0 && successCount == 1 -> "Request cancelled"
					failCount == 0 -> "$successCount requests cancelled"
					successCount == 0 -> "Failed to cancel request${if (failCount > 1) "s" else ""}"
					else -> "$successCount cancelled, $failCount failed"
				}
				
				Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
				
				// Refresh details to update status
				loadFullDetails()
			} catch (e: Exception) {
				Timber.e(e, "Error cancelling requests")
				if (isAdded) {
					Toast.makeText(
						requireContext(),
						"Error: ${e.message}",
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
			// Create a generic YouTube search intent without specifying a package
			// This allows the user to choose their preferred app (YouTube, SmartTube, etc.)
			val youtubeSearchUrl = "https://www.youtube.com/results?search_query=${android.net.Uri.encode(searchQuery)}"
			val intent = android.content.Intent(
				android.content.Intent.ACTION_VIEW,
				android.net.Uri.parse(youtubeSearchUrl)
			)
			
			// Show app chooser to allow user to select their preferred app
			val chooser = android.content.Intent.createChooser(intent, "Play Trailer")
			startActivity(chooser)
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
