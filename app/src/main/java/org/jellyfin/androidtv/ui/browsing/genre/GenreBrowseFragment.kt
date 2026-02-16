package org.jellyfin.androidtv.ui.browsing.genre

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.repository.MultiServerRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.databinding.HorizontalGridBrowseBinding
import org.jellyfin.androidtv.databinding.PopupEmptyBinding
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.AlphaPickerView
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.androidtv.util.sdk.ApiClientFactory
import org.jellyfin.androidtv.util.sdk.compat.copyWithServerId
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.UUID

/**
 * Sort options for browsing genre items
 */
data class GenreItemSortOption(
	val name: String,
	val sortBy: ItemSortBy,
	val sortOrder: SortOrder
)

/**
 * Fragment that displays items from a specific genre in a grid layout.
 * Uses the same layout and styling as JellyseerrBrowseByFragment for consistency.
 */
class GenreBrowseFragment : Fragment() {
	private val apiClient by inject<ApiClient>()
	private val backgroundService by inject<BackgroundService>()
	private val itemLauncher by inject<ItemLauncher>()
	private val multiServerRepository by inject<MultiServerRepository>()
	private val userPreferences by inject<UserPreferences>()
	private val apiClientFactory by inject<ApiClientFactory>()
	private val navigationRepository by inject<NavigationRepository>()

	private var binding: HorizontalGridBrowseBinding? = null
	
	private lateinit var gridAdapter: ArrayObjectAdapter
	private lateinit var gridPresenter: VerticalGridPresenter
	private var gridViewHolder: VerticalGridPresenter.ViewHolder? = null

	private var genreName: String = ""
	private var parentId: UUID? = null
	private var includeType: String? = null
	private var serverId: UUID? = null

	private var currentPage = 0
	private var totalItems = 0
	private var isLoading = false
	private val pageSize = 50

	private var currentSortOption: GenreItemSortOption = SORT_OPTIONS[1]
	private var sortButton: ImageButton? = null
	
	private var startLetter: String? = null
	private var letterButton: ImageButton? = null
	private var jumplistPopup: JumplistPopup? = null

	companion object {
		const val ARG_GENRE_NAME = "genre_name"
		const val ARG_PARENT_ID = "parent_id"
		const val ARG_INCLUDE_TYPE = "include_type"
		const val ARG_SERVER_ID = "server_id"
		private const val NUM_COLUMNS = 7

		val SORT_OPTIONS = listOf(
			GenreItemSortOption("Popularity", ItemSortBy.COMMUNITY_RATING, SortOrder.DESCENDING),
			GenreItemSortOption("Name (A-Z)", ItemSortBy.SORT_NAME, SortOrder.ASCENDING),
			GenreItemSortOption("Name (Z-A)", ItemSortBy.SORT_NAME, SortOrder.DESCENDING),
			GenreItemSortOption("Date Added", ItemSortBy.DATE_CREATED, SortOrder.DESCENDING),
			GenreItemSortOption("Release Date", ItemSortBy.PREMIERE_DATE, SortOrder.DESCENDING),
			GenreItemSortOption("Critic Rating", ItemSortBy.CRITIC_RATING, SortOrder.DESCENDING),
		)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		arguments?.let {
			genreName = it.getString(ARG_GENRE_NAME) ?: ""
			it.getString(ARG_PARENT_ID)?.let { id -> parentId = UUID.fromString(id) }
			includeType = it.getString(ARG_INCLUDE_TYPE)
			it.getString(ARG_SERVER_ID)?.let { id -> serverId = UUID.fromString(id) }
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
		
		val isFirstLoad = !::gridAdapter.isInitialized || gridAdapter.size() == 0
		setupGrid()
		
		// Only load content if this is the first time or if adapter is empty
		if (isFirstLoad) {
			loadContent()
		}
	}

	private fun setupHeader() {
		binding?.apply {
			filterLogo.text = genreName
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
		
		sortButton = ImageButton(context, null, 0, R.style.Button_Icon).apply {
			setImageResource(R.drawable.ic_sort)
			maxHeight = buttonSize
			adjustViewBounds = true
			contentDescription = getString(R.string.lbl_sort_by)
			setOnClickListener { showSortMenu() }
		}
		toolbar.addView(sortButton)
		
		jumplistPopup = JumplistPopup()
		letterButton = ImageButton(context, null, 0, R.style.Button_Icon).apply {
			setImageResource(R.drawable.ic_jump_letter)
			maxHeight = buttonSize
			adjustViewBounds = true
			contentDescription = getString(R.string.lbl_by_letter)
			setOnClickListener { jumplistPopup?.show() }
		}
		toolbar.addView(letterButton)
	}

	private fun showSortMenu() {
		val context = context ?: return
		val toolbar = binding?.toolBar ?: return
		
		val sortMenu = PopupMenu(context, toolbar, Gravity.END)
		
		SORT_OPTIONS.forEachIndexed { index, option ->
			val item = sortMenu.menu.add(0, index, index, option.name)
			item.isChecked = option.sortBy == currentSortOption.sortBy && option.sortOrder == currentSortOption.sortOrder
		}
		sortMenu.menu.setGroupCheckable(0, true, true)
		
		sortMenu.setOnMenuItemClickListener { item ->
			val selectedOption = SORT_OPTIONS[item.itemId]
			if (selectedOption.sortBy != currentSortOption.sortBy || selectedOption.sortOrder != currentSortOption.sortOrder) {
				currentSortOption = selectedOption
				item.isChecked = true
				resetAndReload()
			}
			true
		}
		sortMenu.show()
	}

	private fun resetAndReload() {
		currentPage = 0
		gridAdapter.clear()
		loadContent()
	}

	private fun setupGrid() {
		val zoomFactor = if (userPreferences[UserPreferences.cardFocusExpansion]) FocusHighlight.ZOOM_FACTOR_MEDIUM else FocusHighlight.ZOOM_FACTOR_NONE
		gridPresenter = VerticalGridPresenter(zoomFactor, false).apply {
			numberOfColumns = NUM_COLUMNS
			shadowEnabled = false
		}

		// Only create a new adapter if one doesn't exist
		if (!::gridAdapter.isInitialized) {
			// Use larger card height for bigger posters
			val cardPresenter = CardPresenter(true, 180)
			gridAdapter = ArrayObjectAdapter(cardPresenter)
		}

		gridPresenter.setOnItemViewSelectedListener(OnItemViewSelectedListener { _, item, _, _ ->
			if (item is BaseRowItem) {
				onItemSelected(item)
				
				// Load more when near the end
				val position = gridAdapter.indexOf(item)
				if (position >= gridAdapter.size() - 10 && !isLoading && gridAdapter.size() < totalItems) {
					loadContent()
				}
			}
		})

		gridPresenter.setOnItemViewClickedListener(OnItemViewClickedListener { _, item, _, _ ->
			val rowItem = item as? BaseRowItem ?: return@OnItemViewClickedListener
			itemLauncher.launch(rowItem, null, requireActivity())
		})

		// Create grid view and add to container
		binding?.rowsFragment?.let { container ->
			// Remove any padding on the container and allow children to draw outside bounds
			container.setPadding(0, 0, 0, 0)
			container.clipChildren = false
			container.clipToPadding = false
			
			gridViewHolder = gridPresenter.onCreateViewHolder(container) as VerticalGridPresenter.ViewHolder
			gridViewHolder?.let { holder ->
				val gridView = holder.gridView
				gridView.setNumColumns(NUM_COLUMNS)
				val verticalSpacing = Utils.convertDpToPixel(requireContext(), 20)
				gridView.setVerticalSpacing(verticalSpacing)
				
				// Remove horizontal padding/margins - align items to edges
				gridView.windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
				gridView.windowAlignmentOffsetPercent = 0f
				gridView.windowAlignmentOffset = 0
				gridView.itemAlignmentOffsetPercent = 0f
				gridView.itemAlignmentOffset = 0
				
				// Move grid lower with more top padding, allow overflow for focus zoom
				val topPadding = Utils.convertDpToPixel(requireContext(), 80)
				gridView.setPadding(0, topPadding, 0, 0)
				gridView.clipToPadding = false
				gridView.clipChildren = false
				
				// Also set on the holder view
				(holder.view as? ViewGroup)?.apply {
					clipChildren = false
					clipToPadding = false
					setPadding(0, 0, 0, 0)
				}
				
				container.removeAllViews()
				container.addView(holder.view)
				gridPresenter.onBindViewHolder(holder, gridAdapter)
			}
		}
	}

	private fun onItemSelected(item: BaseRowItem) {
		item.baseItem?.let { baseItem ->
			backgroundService.setBackground(baseItem)
		}
		
		binding?.title?.apply {
			text = item.baseItem?.name ?: getString(R.string.lbl_bracket_unknown)
			visibility = View.VISIBLE
		}
		
		// Show and update info row with metadata
		binding?.infoRow?.visibility = View.VISIBLE
		updateInfoRow(item.baseItem)
		
		// Update counter with position
		val position = gridAdapter.indexOf(item) + 1
		updateCounter(position)
	}

	private fun updateInfoRow(item: BaseItemDto?) {
		val context = context ?: return
		val infoRow = binding?.infoRow ?: return
		if (item == null) return
		
		// Clear existing views
		infoRow.removeAllViews()
		
		// Add metadata items similar to JellyseerrBrowseByFragment
		val metadataItems = mutableListOf<String>()
		
		// Year
		item.productionYear?.let { metadataItems.add(it.toString()) }
		
		// Rating (if available)
		item.communityRating?.let { rating ->
			if (rating > 0) {
				metadataItems.add("★ %.1f".format(rating))
			}
		}
		
		// Runtime for movies
		item.runTimeTicks?.let { ticks ->
			val minutes = (ticks / 600000000).toInt()
			if (minutes > 0) {
				val hours = minutes / 60
				val mins = minutes % 60
				if (hours > 0) {
					metadataItems.add("${hours}h ${mins}m")
				} else {
					metadataItems.add("${mins}m")
				}
			}
		}
		
		// Media type
		val typeLabel = when (item.type) {
			BaseItemKind.MOVIE -> getString(R.string.lbl_movies)
			BaseItemKind.SERIES -> getString(R.string.lbl_tv_series)
			else -> ""
		}
		if (typeLabel.isNotEmpty()) metadataItems.add(typeLabel)
		
		// Official rating (PG-13, etc)
		item.officialRating?.let { if (it.isNotEmpty()) metadataItems.add(it) }
		
		// Add metadata text views
		metadataItems.forEachIndexed { index, text ->
			if (index > 0) {

				val separator = TextView(context).apply {
					this.text = " • "
					textSize = 14f
					setTextColor(Color.WHITE)
					alpha = 0.7f
				}
				infoRow.addView(separator)
			}
			
			val textView = TextView(context).apply {
				this.text = text
				textSize = 14f
				setTextColor(Color.WHITE)
				alpha = 0.7f
			}
			infoRow.addView(textView)
		}
	}

	private fun updateStatusText() {
		val sortName = currentSortOption.name
		
		val letterText = if (startLetter != null) "${getString(R.string.lbl_starting_with)} $startLetter " else ""
		binding?.statusText?.text = "${getString(R.string.lbl_sorted_by)} $sortName${ if (letterText.isNotEmpty()) " • $letterText" else "" }"
	}

	private fun loadContent() {
		if (isLoading) return
		isLoading = true

		binding?.counter?.text = "..."

		lifecycleScope.launch {
			try {
				val includeTypes = when (includeType) {
					"Movie" -> setOf(BaseItemKind.MOVIE)
					"Series" -> setOf(BaseItemKind.SERIES)
					else -> setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
				}

				val enableMultiServer = userPreferences[UserPreferences.enableMultiServerLibraries]
				
				if (enableMultiServer && serverId == null) {
					loadMultiServerContent(includeTypes)
				} else {
					loadSingleServerContent(includeTypes)
				}

			} catch (e: Exception) {
				Timber.e(e, "Failed to load genre items")
				binding?.statusText?.text = getString(R.string.msg_error_loading_data)
				binding?.statusText?.visibility = View.VISIBLE
			} finally {
				isLoading = false
			}
		}
	}
	
	private suspend fun loadSingleServerContent(includeTypes: Set<BaseItemKind>) {
		val targetApi = if (serverId != null) {
			apiClientFactory.getApiClientForServer(serverId!!) ?: apiClient
		} else {
			apiClient
		}
		
		val response = withContext(Dispatchers.IO) {
			targetApi.itemsApi.getItems(
				parentId = parentId,
				genres = setOf(genreName),
				includeItemTypes = includeTypes,
				recursive = true,
				sortBy = setOf(currentSortOption.sortBy),
				sortOrder = setOf(currentSortOption.sortOrder),
				startIndex = currentPage * pageSize,
				limit = pageSize,
				fields = ItemRepository.itemFields,
				enableTotalRecordCount = true,
				nameStartsWith = startLetter,
			).content
		}

		totalItems = response.totalRecordCount ?: 0

		val rowItems = response.items.map { item ->
			val annotatedItem = if (serverId != null) item.copyWithServerId(serverId.toString()) else item
			BaseItemDtoBaseRowItem(annotatedItem)
		}
		gridAdapter.addAll(gridAdapter.size(), rowItems)

		currentPage++
		
		updateCounter(if (gridAdapter.size() > 0) 1 else 0)
		updateStatusText()

		if (gridAdapter.size() == 0) {
			binding?.statusText?.text = getString(R.string.lbl_no_items)
			binding?.statusText?.visibility = View.VISIBLE
		}
	}
	
	private suspend fun loadMultiServerContent(includeTypes: Set<BaseItemKind>) {
		val sessions = multiServerRepository.getLoggedInServers()
		
		if (sessions.isEmpty()) {
			loadSingleServerContent(includeTypes)
			return
		}
		
		// Load from all servers in parallel
		val allItems = withContext(Dispatchers.IO) {
			sessions.flatMap { session ->
				try {
					val response = session.apiClient.itemsApi.getItems(
						genres = setOf(genreName),
						includeItemTypes = includeTypes,
						recursive = true,
						sortBy = setOf(currentSortOption.sortBy),
						sortOrder = setOf(currentSortOption.sortOrder),
						limit = pageSize,
						fields = ItemRepository.itemFields,
						enableTotalRecordCount = true,
						nameStartsWith = startLetter,
					).content
					
					response.items.map { it.copyWithServerId(session.server.id.toString()) }
				} catch (e: Exception) {
					Timber.e(e, "Failed to load genre items from server ${session.server.name}")
					emptyList()
				}
			}
		}
		
		// Sort combined results
		val sortedItems = when (currentSortOption.sortOrder) {
			SortOrder.ASCENDING -> when (currentSortOption.sortBy) {
				ItemSortBy.SORT_NAME -> allItems.sortedBy { it.name?.lowercase() }
				ItemSortBy.COMMUNITY_RATING -> allItems.sortedBy { it.communityRating ?: 0f }
				ItemSortBy.DATE_CREATED -> allItems.sortedBy { it.dateCreated }
				ItemSortBy.PREMIERE_DATE -> allItems.sortedBy { it.premiereDate }
				ItemSortBy.CRITIC_RATING -> allItems.sortedBy { it.criticRating ?: 0f }
				else -> allItems
			}
			else -> when (currentSortOption.sortBy) {
				ItemSortBy.SORT_NAME -> allItems.sortedByDescending { it.name?.lowercase() }
				ItemSortBy.COMMUNITY_RATING -> allItems.sortedByDescending { it.communityRating ?: 0f }
				ItemSortBy.DATE_CREATED -> allItems.sortedByDescending { it.dateCreated }
				ItemSortBy.PREMIERE_DATE -> allItems.sortedByDescending { it.premiereDate }
				ItemSortBy.CRITIC_RATING -> allItems.sortedByDescending { it.criticRating ?: 0f }
				else -> allItems
			}
		}
		
		totalItems = sortedItems.size
		
		val rowItems = sortedItems.map { item ->
			BaseItemDtoBaseRowItem(item)
		}
		gridAdapter.addAll(gridAdapter.size(), rowItems)
		
		updateCounter(if (gridAdapter.size() > 0) 1 else 0)
		updateStatusText()

		if (gridAdapter.size() == 0) {
			binding?.statusText?.text = getString(R.string.lbl_no_items)
			binding?.statusText?.visibility = View.VISIBLE
		}
	}

	private fun updateCounter(position: Int) {
		binding?.counter?.text = "$position | $totalItems"
	}

	override fun onDestroyView() {
		super.onDestroyView()
		jumplistPopup?.dismiss()
		binding = null
		gridViewHolder = null
		sortButton = null
		letterButton = null
		jumplistPopup = null
	}
	
	/**
	 * Popup for jumping to a specific letter in alphabetical lists
	 */
	inner class JumplistPopup {
		private val popupWidth = Utils.convertDpToPixel(requireContext(), 900)
		private val popupHeight = Utils.convertDpToPixel(requireContext(), 55)
		
		private var popupWindow: PopupWindow? = null
		private var alphaPicker: AlphaPickerView? = null
		
		init {
			val layout = PopupEmptyBinding.inflate(layoutInflater, binding?.rowsFragment, false)
			popupWindow = PopupWindow(layout.emptyPopup, popupWidth, popupHeight, true).apply {
				isOutsideTouchable = true
				animationStyle = R.style.WindowAnimation_SlideTop
			}
			
			alphaPicker = AlphaPickerView(requireContext(), null).apply {
				onAlphaSelected = { letter ->
					startLetter = if (letter == '#') null else letter.toString()
					resetAndReload()
					dismiss()
				}
			}
			
			layout.emptyPopup.addView(alphaPicker)
		}
		
		fun show() {
			binding?.rowsFragment?.let { container ->
				popupWindow?.showAtLocation(container, Gravity.TOP, container.left, container.top)
				startLetter?.firstOrNull()?.let { letter ->
					alphaPicker?.focus(letter)
				}
			}
		}
		
		fun dismiss() {
			popupWindow?.takeIf { it.isShowing }?.dismiss()
		}
	}
}
