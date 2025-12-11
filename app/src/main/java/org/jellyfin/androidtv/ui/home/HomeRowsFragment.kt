package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.CustomMessage
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.CustomMessageRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.browsing.CompositeClickedListener
import org.jellyfin.androidtv.ui.browsing.CompositeSelectedListener
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.itemhandling.refreshItem
import org.jellyfin.androidtv.ui.home.mediabar.MediaBarSlideshowViewModel
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.AudioEventListener
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.util.KeyProcessor
import org.jellyfin.androidtv.util.Debouncer
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HomeRowsFragment : RowsSupportFragment(), AudioEventListener, View.OnKeyListener {
	private val api by inject<ApiClient>()
	private val backgroundService by inject<BackgroundService>()
	private val playbackManager by inject<PlaybackManager>()
	private val mediaManager by inject<MediaManager>()
	private val notificationsRepository by inject<NotificationsRepository>()
	private val userRepository by inject<UserRepository>()
	private val userPreferences by inject<UserPreferences>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val userViewsRepository by inject<UserViewsRepository>()
	private val dataRefreshService by inject<DataRefreshService>()
	private val customMessageRepository by inject<CustomMessageRepository>()
	private val navigationRepository by inject<NavigationRepository>()
	private val itemLauncher by inject<ItemLauncher>()
	private val keyProcessor by inject<KeyProcessor>()
	private val mediaBarViewModel by inject<MediaBarSlideshowViewModel>()

	private val helper by lazy { HomeFragmentHelper(requireContext(), userRepository) }

	// Flow to track selected row position
	private val _selectedPositionFlow = MutableStateFlow(0)
	val selectedPositionFlow: StateFlow<Int> = _selectedPositionFlow.asStateFlow()

	// Flow to track selected item for split view display
	private val _selectedItemStateFlow = MutableStateFlow(SelectedItemState.EMPTY)
	val selectedItemStateFlow: StateFlow<SelectedItemState> = _selectedItemStateFlow.asStateFlow()

	// Data
	private var currentItem: BaseRowItem? = null
	private var currentRow: ListRow? = null
	private var justLoaded = true

	// Special rows
	private val notificationsRow by lazy { NotificationsHomeFragmentRow(lifecycleScope, notificationsRepository) }
	private val nowPlaying by lazy { HomeFragmentNowPlayingRow(lifecycleScope, playbackManager, mediaManager) }
	private val liveTVRow by lazy { HomeFragmentLiveTVRow(requireActivity(), userRepository, navigationRepository) }
	private val mediaBarRow by lazy { HomeFragmentMediaBarRow(lifecycleScope, mediaBarViewModel) }

	// Store rows for refreshing
	private var currentRows = mutableListOf<HomeFragmentRow>()
	private var playlistRow: HomeFragmentMoonfinPlaylistRow? = null

	// Debouncer for selection updates - only update UI after user stops navigating
	private val selectionDebouncer by lazy { Debouncer(150.milliseconds, lifecycleScope) }
	private val backgroundDebouncer by lazy { Debouncer(200.milliseconds, lifecycleScope) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// Create a custom row presenter that keeps headers always visible
		val rowPresenter = PositionableListRowPresenter(requireContext()).apply {
			// Enable select effect for rows
			setSelectEffectEnabled(true)
		}

		// Create presenter selector to handle different row types
		val presenterSelector = ClassPresenterSelector().apply {
			addClassPresenter(ListRow::class.java, rowPresenter)
			addClassPresenter(MediaBarRow::class.java, MediaBarPresenter(mediaBarViewModel, navigationRepository))
		}

		adapter = MutableObjectAdapter<Row>(presenterSelector)

		lifecycleScope.launch(Dispatchers.IO) {
			val currentUser = withTimeout(30.seconds) {
				userRepository.currentUser.filterNotNull().first()
			}

			// Start out with default sections
			val homesections = userSettingPreferences.activeHomesections

			// Make sure the rows are empty
			val rows = mutableListOf<HomeFragmentRow>()

			// Check for coroutine cancellation
			if (!isActive) return@launch

			// Actually add the sections
			val mergeContinueWatching = userPreferences[UserPreferences.mergeContinueWatchingNextUp]
			var mergedRowAdded = false
			
			for (section in homesections) when (section) {
				HomeSectionType.MEDIA_BAR -> rows.add(mediaBarRow) // Add Media Bar as a native row
				HomeSectionType.LATEST_MEDIA -> rows.add(helper.loadRecentlyAdded(userViewsRepository.views.first()))
				HomeSectionType.LIBRARY_TILES_SMALL -> rows.add(HomeFragmentViewsRow(small = false))
				HomeSectionType.LIBRARY_BUTTONS -> rows.add(HomeFragmentViewsRow(small = true))
				HomeSectionType.RESUME -> {
					if (mergeContinueWatching && !mergedRowAdded) {
						rows.add(helper.loadMergedContinueWatching())
						mergedRowAdded = true
					} else if (!mergeContinueWatching) {
						rows.add(helper.loadResumeVideo())
					}
				}
				HomeSectionType.RESUME_AUDIO -> rows.add(helper.loadResumeAudio())
				HomeSectionType.RESUME_BOOK -> Unit // Books are not (yet) supported
				HomeSectionType.ACTIVE_RECORDINGS -> rows.add(helper.loadLatestLiveTvRecordings())
				HomeSectionType.NEXT_UP -> {
					// Skip Next Up if already merged with Continue Watching
					if (!mergeContinueWatching) {
						rows.add(helper.loadNextUp())
					} else if (!mergedRowAdded) {
						// If user has Next Up but not Resume in their section list, add merged row here
						rows.add(helper.loadMergedContinueWatching())
						mergedRowAdded = true
					}
				}
				HomeSectionType.PLAYLIST -> {
					val playlistRow = helper.loadPlaylists()
					if (playlistRow is HomeFragmentMoonfinPlaylistRow) {
						this@HomeRowsFragment.playlistRow = playlistRow
					}
					rows.add(playlistRow)
				}
				HomeSectionType.LIVE_TV -> Unit // Live TV has its own dedicated page, no rows on home screen

				HomeSectionType.NONE -> Unit
			}

			// Store rows for refreshing
			currentRows = rows

			// Add sections to layout
			withContext(Dispatchers.Main) {
				val cardPresenter = CardPresenter()

				// Add rows in order
				notificationsRow.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)
				nowPlaying.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)
				for (row in rows) row.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)
			}
		}

		onItemViewClickedListener = CompositeClickedListener().apply {
			registerListener(ItemViewClickedListener())
			registerListener(liveTVRow::onItemClicked)
			registerListener(notificationsRow::onItemClicked)
		}

		onItemViewSelectedListener = CompositeSelectedListener().apply {
			registerListener(ItemViewSelectedListener())
		}

		customMessageRepository.message
			.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { message ->
				when (message) {
					CustomMessage.RefreshCurrentItem -> refreshCurrentItem()
					else -> Unit
				}
			}.launchIn(lifecycleScope)

		// Listen for merge continue watching preference changes and recreate rows
		var lastMergeState = userPreferences[UserPreferences.mergeContinueWatchingNextUp]
		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				// Check preference every 500ms while resumed
				while (true) {
					delay(500.milliseconds)
					val currentMergeState = userPreferences[UserPreferences.mergeContinueWatchingNextUp]
					if (currentMergeState != lastMergeState) {
						lastMergeState = currentMergeState
						// Recreate the fragment to rebuild rows with new structure
						parentFragmentManager.beginTransaction()
							.detach(this@HomeRowsFragment)
							.commitNow()
						parentFragmentManager.beginTransaction()
							.attach(this@HomeRowsFragment)
							.commitNow()
					}
				}
			}
		}

		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				api.webSocket.subscribe<UserDataChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)

				api.webSocket.subscribe<LibraryChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)
			}
		}

		// Subscribe to Audio messages
		mediaManager.addAudioEventListener(this)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		
		// Enable hardware acceleration for smoother scrolling
		view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
		
		// Configure the vertical grid view to not release focus upward
		verticalGridView?.apply {
			// Enable hardware acceleration on the grid view
			setLayerType(View.LAYER_TYPE_HARDWARE, null)
			
			// Reduce item prefetch distance for faster initial load
			setItemViewCacheSize(20)
			
			setOnKeyListener { v, keyCode, event ->
				// Handle upward navigation from first row to toolbar
				if (event.action == android.view.KeyEvent.ACTION_DOWN &&
					keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP &&
					selectedPosition == 0) {
					// Try to move focus to the toolbar when on the first row
					try {
						val decor = requireActivity().window.decorView
						val toolbarActions = decor.findViewById<View?>(org.jellyfin.androidtv.R.id.toolbar_actions)
						if (toolbarActions != null && toolbarActions.isFocusable) {
							toolbarActions.requestFocus()
							return@setOnKeyListener true
						}
					} catch (t: Throwable) {
						// ignore - fall through to allow default behavior
					}

					// Let the system handle the focus (do not consume) so it can move to title if possible
					false
				} else {
					false
				}
			}
		}
	}

	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
		if (event?.action != KeyEvent.ACTION_UP) return false
		return keyProcessor.handleKey(keyCode, currentItem, activity)
	}

	override fun onResume() {
		super.onResume()

		//React to deletion
		if (currentRow != null && currentItem != null && currentItem?.baseItem != null && currentItem!!.baseItem!!.id == dataRefreshService.lastDeletedItemId) {
			(currentRow!!.adapter as ItemRowAdapter).remove(currentItem)
			currentItem = null
			dataRefreshService.lastDeletedItemId = null
		}

		if (!justLoaded) {
			// Always refresh all rows and data when returning to home to get latest from server
			refreshRows(force = true, delayed = true) // Force refresh to get latest data
			
			// Reload media bar with fresh random items when returning to home
			mediaBarViewModel.loadInitialContent()
		} else {
			justLoaded = false
			// Load initial content on first load
			mediaBarViewModel.loadInitialContent()
		}

		// Update audio queue
		Timber.i("Updating audio queue in HomeFragment (onResume)")
		nowPlaying.update(requireContext(), adapter as MutableObjectAdapter<Row>)
		
		// Ensure focus is restored to the grid when returning from other screens (like search)
		// This prevents the issue where users can't control the media bar after backing out
		view?.postDelayed({
			if (isResumed && verticalGridView != null && !verticalGridView!!.hasFocus()) {
				verticalGridView?.requestFocus()
			}
		}, 100) // Small delay to let the fragment fully resume
	}

	override fun onQueueStatusChanged(hasQueue: Boolean) {
		if (activity == null || requireActivity().isFinishing) return

		Timber.i("Updating audio queue in HomeFragment (onQueueStatusChanged)")
		nowPlaying.update(requireContext(), adapter as MutableObjectAdapter<Row>)
	}

	private fun refreshRows(force: Boolean = false, delayed: Boolean = true) {
		lifecycleScope.launch(Dispatchers.IO) {
			if (delayed) delay(1.5.seconds)

			repeat(adapter.size()) { i ->
				val rowAdapter = (adapter[i] as? ListRow)?.adapter as? ItemRowAdapter
				if (force) rowAdapter?.Retrieve()
				else rowAdapter?.ReRetrieveIfNeeded()
			}

			// Refresh playlist row
			playlistRow?.refresh()
		}
	}

	private fun refreshCurrentItem() {
		val adapter = currentRow?.adapter as? ItemRowAdapter ?: return
		val item = currentItem ?: return

		Timber.i("Refresh item ${item.getFullName(requireContext())}")
		adapter.refreshItem(api, this, item)
	}

	override fun onDestroy() {
		super.onDestroy()

		mediaManager.removeAudioEventListener(this)
	}

	private inner class ItemViewClickedListener : OnItemViewClickedListener {
		override fun onItemClicked(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			if (item !is BaseRowItem) return
			if (row !is ListRow) return
			@Suppress("UNCHECKED_CAST")
			itemLauncher.launch(item, row.adapter as MutableObjectAdapter<Any>, requireContext())
		}
	}

	private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
		override fun onItemSelected(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			// Update selected position flow immediately (for focus tracking)
			_selectedPositionFlow.value = selectedPosition
			
			if (item !is BaseRowItem) {
				currentItem = null
				// Clear selected item state immediately
				selectionDebouncer.cancel()
				_selectedItemStateFlow.value = SelectedItemState.EMPTY
				
				// Don't clear background if we're on the media bar row - it has its own backdrop
				if (row !is MediaBarRow) {
					backgroundService.clearBackgrounds()
				}
			} else {
				currentItem = item
				currentRow = row as ListRow

				val itemRowAdapter = row.adapter as? ItemRowAdapter
				itemRowAdapter?.loadMoreItemsIfNeeded(itemRowAdapter.indexOf(item))

				// Debounce UI updates - only update after user stops navigating for 150ms
				selectionDebouncer.debounce {
					_selectedItemStateFlow.value = SelectedItemState(
						title = item.getName(requireContext()) ?: "",
						summary = item.getSummary(requireContext()) ?: "",
						baseItem = item.baseItem
					)
				}

				// Debounce background loading - only load after user stops navigating for 200ms
				backgroundDebouncer.debounce {
					backgroundService.setBackground(item.baseItem)
				}
			}
		}
	}
}
