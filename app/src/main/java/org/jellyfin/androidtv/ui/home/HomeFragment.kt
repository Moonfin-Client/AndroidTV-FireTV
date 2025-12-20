package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.crossfade
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.home.mediabar.MediaBarSlideshowViewModel
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.android.ext.android.inject

class HomeFragment : Fragment() {
	private val mediaBarViewModel by inject<MediaBarSlideshowViewModel>()
	private val userSettingPreferences by inject<UserSettingPreferences>()

	private var titleView: TextView? = null
	private var logoView: ImageView? = null
	private var infoRowView: SimpleInfoRowView? = null
	private var summaryView: TextView? = null
	private var backgroundImage: ImageView? = null
	private var rowsFragment: HomeRowsFragment? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val view = inflater.inflate(R.layout.fragment_home, container, false)

		// Get references to views
		titleView = view.findViewById(R.id.title)
		logoView = view.findViewById(R.id.logo)
		infoRowView = view.findViewById(R.id.infoRow)
		summaryView = view.findViewById(R.id.summary)
		backgroundImage = view.findViewById(R.id.backgroundImage)

		// Setup toolbar Compose
		val toolbarView = view.findViewById<ComposeView>(R.id.toolbar)
		toolbarView.setContent {
			MainToolbar(
				activeButton = MainToolbarActiveButton.Home
			)
		}

		return view
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// Observe selected item state from HomeRowsFragment
		rowsFragment = childFragmentManager.findFragmentById(R.id.rowsFragment) as? HomeRowsFragment

		rowsFragment?.selectedItemStateFlow
			?.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			?.onEach { state ->
				// Update views directly - lightweight View updates (no Compose overhead)
				titleView?.text = state.title
				summaryView?.text = state.summary
				
				// Update info row with metadata - simple property assignment
				infoRowView?.setItem(state.baseItem)
			}
			?.launchIn(lifecycleScope)

		// Observe selected row position to hide media bar backdrop when moving to other rows
		rowsFragment?.selectedPositionFlow
			?.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			?.onEach { position ->
				updateMediaBarBackground()
			}
			?.launchIn(lifecycleScope)

		// Observe media bar state changes (Loading -> Ready transition)
		mediaBarViewModel.state
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { state ->
				updateMediaBarBackground()
			}
			.launchIn(lifecycleScope)

		// Observe media bar focus state for background
		mediaBarViewModel.isFocused
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { isFocused ->
				updateMediaBarBackground()
			}
			.launchIn(lifecycleScope)

		// Observe playback state changes for background updates
		mediaBarViewModel.playbackState
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { playbackState ->
				// Update background when current index changes
				updateMediaBarBackground()
			}
			.launchIn(lifecycleScope)
	}

	private fun updateMediaBarBackground() {
		val state = mediaBarViewModel.state.value
		val isFocused = mediaBarViewModel.isFocused.value
		val selectedPosition = rowsFragment?.selectedPositionFlow?.value ?: -1
		
		// Check if the media bar is enabled in Moonfin settings
		val isMediaBarEnabled = userSettingPreferences[UserSettingPreferences.mediaBarEnabled]
		
		// Determine if we should show media bar content
		// Show if: media bar is focused OR (we're at position 0 AND media bar is enabled) OR position is -1 (toolbar/no selection)
		val shouldShowMediaBar = isFocused || (selectedPosition == 0 && isMediaBarEnabled) || selectedPosition == -1
		
		if (state is org.jellyfin.androidtv.ui.home.mediabar.MediaBarState.Ready && shouldShowMediaBar) {
			val playbackState = mediaBarViewModel.playbackState.value
			val currentItem = state.items.getOrNull(playbackState.currentIndex)
			val backdropUrl = currentItem?.backdropUrl
			val logoUrl = currentItem?.logoUrl
			
			// Show background if we have a backdrop URL
			if (backdropUrl != null) {
				backgroundImage?.isVisible = true
				backgroundImage?.load(backdropUrl) {
					crossfade(400) // 400ms crossfade - faster and smoother
				}
			} else {
				backgroundImage?.isVisible = false
			}
			
			// Show logo if available, otherwise show title
			if (logoUrl != null) {
				logoView?.isVisible = true
				titleView?.isVisible = false
				logoView?.load(logoUrl) {
					crossfade(300) // 300ms crossfade - faster and smoother
				}
			} else {
				logoView?.isVisible = false
				titleView?.isVisible = true
			}
		} else {
			// Hide background and logo when on other rows
			backgroundImage?.isVisible = false
			logoView?.isVisible = false
			titleView?.isVisible = true
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		titleView = null
		logoView = null
		summaryView = null
		infoRowView = null
		backgroundImage = null
		rowsFragment = null
	}
}
