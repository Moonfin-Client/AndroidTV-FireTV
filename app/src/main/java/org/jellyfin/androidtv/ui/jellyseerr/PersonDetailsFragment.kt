package org.jellyfin.androidtv.ui.jellyseerr

import android.graphics.Color
import android.graphics.Outline
import android.os.Bundle
import android.view.Gravity
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.asDrawable
import coil3.load
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrPersonDetailsDto
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.NavbarPosition
import org.jellyfin.androidtv.ui.shared.toolbar.LeftSidebarNavigation
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.util.dp
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale

class PersonDetailsFragment : Fragment() {
	private val viewModel: JellyseerrViewModel by viewModel()
	private val imageLoader: ImageLoader by inject()
	private val backgroundService: BackgroundService by inject()
	private val navigationRepository: NavigationRepository by inject()
	private val userPreferences: UserPreferences by inject()

	private var personId: Int = -1
	private var personName: String = ""
	private var personDetails: JellyseerrPersonDetailsDto? = null
	private var toolbarContainer: View? = null
	private var personInfoContainer: LinearLayout? = null
	private var sidebarId: Int = View.NO_ID

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// Get person info from arguments
		personId = arguments?.getString("personId")?.toIntOrNull() ?: -1
		personName = arguments?.getString("personName") ?: ""

		if (personId == -1) {
			Timber.e("PersonDetailsFragment: No person ID found in arguments")
			Toast.makeText(requireContext(), "Error: Person ID not found", Toast.LENGTH_SHORT).show()
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

		val scrollView = ScrollView(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT
			)
			setBackgroundColor(Color.parseColor("#111827"))
			id = View.generateViewId()
		}

		val rootLayout = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			setPadding(50.dp(context), 80.dp(context), 50.dp(context), 24.dp(context))
		}

		val infoContainer = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			id = View.generateViewId()
		}
		personInfoContainer = infoContainer
		rootLayout.addView(infoContainer)

		scrollView.addView(rootLayout)
		mainContainer.addView(scrollView)

		val navbarPosition = userPreferences[UserPreferences.navbarPosition]
		
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
					id = View.generateViewId()
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
				sidebarId = sidebarOverlay.id
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
					id = View.generateViewId()
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
				sidebarId = topToolbarOverlay.id
				topToolbarContainer.addView(topToolbarOverlay)
				mainContainer.addView(topToolbarContainer)
			}
		}

		return mainContainer
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		loadPersonData()
	}

	private fun loadPersonData() {
		lifecycleScope.launch {
			try {
				// Load person details
				val detailsResult = viewModel.getPersonDetails(personId)
				detailsResult.onSuccess { details ->
					personDetails = details
					updatePersonInfo()
					loadPersonCredits()
				}.onFailure { error ->
					Timber.e(error, "Failed to load person details")
					Toast.makeText(requireContext(), "Failed to load person details", Toast.LENGTH_SHORT).show()
				}
			} catch (e: Exception) {
				Timber.e(e, "Error loading person data")
			}
		}
	}

	private fun updatePersonInfo() {
		val container = personInfoContainer ?: return

		container.removeAllViews()

		// Profile photo and basic info container
		val headerContainer = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.HORIZONTAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = 24.dp(context)
			}
		}

		// Profile photo (circular)
		val profileSize = 120.dp(requireContext())
		val profileImage = ImageView(requireContext()).apply {
			layoutParams = LinearLayout.LayoutParams(profileSize, profileSize).apply {
				marginEnd = 24.dp(context)
			}
			scaleType = ImageView.ScaleType.CENTER_CROP
			setBackgroundColor(Color.parseColor("#1F2937"))

			clipToOutline = true
			outlineProvider = object : ViewOutlineProvider() {
				override fun getOutline(view: View, outline: Outline) {
					outline.setOval(0, 0, view.width, view.height)
				}
			}

			personDetails?.profilePath?.let { path ->
				val imageUrl = "https://image.tmdb.org/t/p/w185$path"
				load(imageUrl)
			}
		}
		headerContainer.addView(profileImage)

		// Name and info
		val infoLayout = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				0,
				LinearLayout.LayoutParams.WRAP_CONTENT,
				1f
			)
		}

		// Name
		val nameText = TextView(requireContext()).apply {
			text = personDetails?.name ?: personName
			textSize = 28f
			setTextColor(Color.WHITE)
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = 8.dp(context)
			}
		}
		infoLayout.addView(nameText)

		// Birth info
		val birthInfo = mutableListOf<String>()
		personDetails?.birthday?.let { birthday ->
			val formattedDate = formatDate(birthday)
			if (formattedDate != null) {
				birthInfo.add("Born $formattedDate")
			}
		}
		personDetails?.placeOfBirth?.let { place ->
			birthInfo.add("in $place")
		}

		if (birthInfo.isNotEmpty()) {
			val birthText = TextView(requireContext()).apply {
				text = birthInfo.joinToString(" ")
				textSize = 14f
				setTextColor(Color.parseColor("#9CA3AF"))
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				)
			}
			infoLayout.addView(birthText)
		}

		headerContainer.addView(infoLayout)
		container.addView(headerContainer)

		// Biography
		personDetails?.biography?.let { bio ->
			if (bio.isNotBlank()) {
				val bioHeading = TextView(requireContext()).apply {
					text = "Biography"
					textSize = 20f
					setTextColor(Color.WHITE)
					setTypeface(typeface, android.graphics.Typeface.BOLD)
					layoutParams = LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT
					).apply {
						topMargin = 16.dp(context)
						bottomMargin = 12.dp(context)
					}
				}
				container.addView(bioHeading)

				var isExpanded = false
				val maxCollapsedLines = 4

				val bioText = TextView(requireContext()).apply {
					text = bio
					textSize = 14f
					setTextColor(Color.parseColor("#D1D5DB"))
					setLineSpacing(6f, 1.5f)
					maxLines = maxCollapsedLines
					ellipsize = android.text.TextUtils.TruncateAt.END
					layoutParams = LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT
					).apply {
						bottomMargin = 8.dp(context)
					}
				}
				container.addView(bioText)

				// Toggle button
				val toggleButton = TextView(requireContext()).apply {
					text = "Show More"
					textSize = 14f
					setTextColor(Color.parseColor("#60A5FA")) // blue-400
					setTypeface(typeface, android.graphics.Typeface.BOLD)
					isFocusable = true
					isFocusableInTouchMode = true
					nextFocusLeftId = sidebarId
					setPadding(8.dp(context), 8.dp(context), 8.dp(context), 8.dp(context))
					layoutParams = LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.WRAP_CONTENT,
						LinearLayout.LayoutParams.WRAP_CONTENT
					).apply {
						bottomMargin = 16.dp(context)
					}

					setOnFocusChangeListener { view, hasFocus ->
						if (hasFocus) {
							setBackgroundColor(Color.parseColor("#374151")) // gray-700
						} else {
							setBackgroundColor(Color.TRANSPARENT)
						}
					}

					setOnClickListener {
						isExpanded = !isExpanded
						if (isExpanded) {
							bioText.maxLines = Integer.MAX_VALUE
							bioText.ellipsize = null
							text = "Show Less"
						} else {
							bioText.maxLines = maxCollapsedLines
							bioText.ellipsize = android.text.TextUtils.TruncateAt.END
							text = "Show More"
						}
					}
				}
				container.addView(toggleButton)
			}
		}
	}

	private fun loadPersonCredits() {
		val container = personInfoContainer ?: return

		lifecycleScope.launch {
			try {
				val creditsResult = viewModel.getPersonCombinedCredits(personId)
				creditsResult.onSuccess { credits ->
					// Filter and sort cast appearances
					val appearances = credits.cast
						.filter { it.posterPath != null } // Only show items with posters
						.sortedBy { it.title ?: it.name ?: "" } // Sort alphabetically by title/name

					if (appearances.isNotEmpty()) {
						container.addView(createAppearancesSection(appearances))
					}
				}.onFailure { error ->
					Timber.e(error, "Failed to load person credits")
				}
			} catch (e: Exception) {
				Timber.e(e, "Error loading person credits")
			}
		}
	}

	private fun createAppearancesSection(appearances: List<JellyseerrDiscoverItemDto>): View {
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 16.dp(context)
			}
		}

		val heading = TextView(requireContext()).apply {
			text = "Appearances"
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
		container.addView(heading)

		// Create a grid layout for movies (3 columns)
		val itemsPerRow = 5
		val cardWidth = 150.dp(requireContext())
		val cardSpacing = 16.dp(requireContext())

		var currentRow: LinearLayout? = null
		appearances.forEachIndexed { index, item ->
			if (index % itemsPerRow == 0) {
				// Create new row
				currentRow = LinearLayout(requireContext()).apply {
					orientation = LinearLayout.HORIZONTAL
					layoutParams = LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT
					).apply {
						bottomMargin = cardSpacing
					}
				}
				container.addView(currentRow)
			}

			val card = createMovieCard(item)
			currentRow?.addView(card)
		}

		return container
	}

	private fun createMovieCard(item: JellyseerrDiscoverItemDto): View {
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
			nextFocusLeftId = sidebarId

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
				// Navigate to the media details
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
		PosterBadges.addToContainer(requireContext(), imageContainer, item)
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
			val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
			val date = inputFormat.parse(dateString)
			date?.let { outputFormat.format(it) }
		} catch (e: Exception) {
			Timber.e(e, "Failed to parse date: $dateString")
			null
		}
	}
}
