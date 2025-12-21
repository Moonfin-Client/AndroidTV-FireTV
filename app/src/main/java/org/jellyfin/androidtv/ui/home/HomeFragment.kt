package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.home.mediabar.MediaBarSlideshowViewModel
import org.jellyfin.androidtv.ui.home.mediabar.MediaBarState
import org.jellyfin.androidtv.ui.shared.LogoView
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.android.ext.android.inject
import timber.log.Timber

class HomeFragment : Fragment() {
	private val mediaBarViewModel by inject<MediaBarSlideshowViewModel>()
	private val userSettingPreferences by inject<UserSettingPreferences>()

	private var titleView: TextView? = null
	private var nameView: TextView? = null
	private var logoView: ComposeView? = null
	private var infoRowView: SimpleInfoRowView? = null
	private var summaryView: TextView? = null
	private var backgroundImage: ComposeView? = null
	private var rowsFragment: HomeRowsFragment? = null

	// Compose-observable state for rowsFragment - triggers recomposition when fragment becomes available
	private val rowsFragmentState = mutableStateOf<HomeRowsFragment?>(null)

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val view = inflater.inflate(R.layout.fragment_home, container, false)

		// Get references to views
		titleView = view.findViewById(R.id.title)
		nameView = view.findViewById(R.id.name)
		infoRowView = view.findViewById(R.id.infoRow)
		summaryView = view.findViewById(R.id.summary)

		// Setup logo with Crossfade for smooth transitions
		// This shows logos for Continue Watching and other non-media-bar rows
		logoView = view.findViewById<ComposeView>(R.id.logo).apply {
			setContent {
				// Use rowsFragmentState to trigger recomposition when fragment becomes available
				val fragment by rowsFragmentState
				val selectedItemState = fragment?.selectedItemStateFlow?.collectAsState()?.value
				val selectedPosition = fragment?.selectedPositionFlow?.collectAsState(initial = -1)?.value ?: -1
				val isFocused by mediaBarViewModel.isFocused.collectAsState()

				// Check if the media bar is enabled in Moonfin settings
				val isMediaBarEnabled = userSettingPreferences[UserSettingPreferences.mediaBarEnabled]
				// Determine if we're on the media bar row
				val isOnMediaBar = isFocused || (selectedPosition == 0 && isMediaBarEnabled) || selectedPosition == -1

				// For non-media-bar rows, use the logo from selected item state
				// Media bar has its own logo rendering in MediaBarSlideshowView
				val logoUrl = if (!isOnMediaBar) {
					selectedItemState?.logoUrl
				} else null

				Timber.d("HomeFragment Logo: position=%d, isFocused=%b, isOnMediaBar=%b, logoUrl=%s, itemName=%s",
					selectedPosition, isFocused, isOnMediaBar, logoUrl?.take(50), selectedItemState?.name)

				Crossfade(
					targetState = logoUrl,
					animationSpec = tween(300),
					label = "logo_transition"
				) { url ->
					Timber.d("HomeFragment Logo Crossfade: url=%s", url?.take(50))
					if (url != null) {
						Box(
							modifier = Modifier
								.fillMaxSize()
								.padding(horizontal = 16.dp, vertical = 16.dp),
							contentAlignment = Alignment.Center
						) {
							// Use shared LogoView with adaptive shadow color
							LogoView(
								url = url,
								modifier = Modifier.fillMaxWidth()
							)
						}
					}
				}
			}
		}

		// Setup background with AnimatedContent for smooth transitions
		backgroundImage = view.findViewById<ComposeView>(R.id.backgroundImage).apply {
			setContent {
				val state by mediaBarViewModel.state.collectAsState()
				val playbackState by mediaBarViewModel.playbackState.collectAsState()
				val isFocused by mediaBarViewModel.isFocused.collectAsState()

				// Use rowsFragmentState to trigger recomposition when fragment becomes available
				val fragment by rowsFragmentState
				val selectedPosition = fragment?.selectedPositionFlow?.collectAsState(initial = -1)?.value ?: -1
				// Check if the media bar is enabled in Moonfin settings
				val isMediaBarEnabled = userSettingPreferences[UserSettingPreferences.mediaBarEnabled]
				// Show media bar when: focused OR (at position 0 AND enabled) OR at position -1 (toolbar)
				val shouldShowMediaBar = isFocused || (selectedPosition == 0 && isMediaBarEnabled) || selectedPosition == -1

				val backdropUrl = if (state is MediaBarState.Ready && shouldShowMediaBar) {
					(state as MediaBarState.Ready).items.getOrNull(playbackState.currentIndex)?.backdropUrl
				} else null

				Crossfade(
					targetState = backdropUrl,
					animationSpec = tween(300),
					label = "backdrop_transition"
				) { url ->
					if (url != null) {
						AsyncImage(
							model = url,
							contentDescription = null,
							modifier = Modifier.fillMaxSize(),
							contentScale = ContentScale.Crop
						)
					}
				}
			}
		}

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
		// Update Compose-observable state to trigger recomposition in logo and background composables
		rowsFragmentState.value = rowsFragment

		rowsFragment?.selectedItemStateFlow
			?.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			?.onEach { state ->
				// Update views directly - lightweight View updates (no Compose overhead)
				titleView?.text = state.title
				nameView?.text = state.name
				summaryView?.text = state.summary

				// Update info row with metadata - simple property assignment
				infoRowView?.setItem(state.baseItem)

				// Update logo visibility when selected item changes
				updateLogoVisibility()
			}
			?.launchIn(lifecycleScope)

		// Update logo visibility based on whether we're showing media bar or not
		rowsFragment?.selectedPositionFlow
			?.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			?.onEach { position ->
				updateLogoVisibility()
				updateDetailsVisibility(position)
			}
			?.launchIn(lifecycleScope)

		// Observe media bar state changes
		mediaBarViewModel.state
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { state ->
				updateLogoVisibility()
			}
			.launchIn(lifecycleScope)

		// Observe media bar focus state
		mediaBarViewModel.isFocused
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { isFocused ->
				updateLogoVisibility()
			}
			.launchIn(lifecycleScope)
	}

	private fun updateLogoVisibility() {
		val isFocused = mediaBarViewModel.isFocused.value
		val selectedPosition = rowsFragment?.selectedPositionFlow?.value ?: -1
		val selectedItemState = rowsFragment?.selectedItemStateFlow?.value

		// Check if the media bar is enabled in Moonfin settings
		val isMediaBarEnabled = userSettingPreferences[UserSettingPreferences.mediaBarEnabled]

		// Determine if we should show media bar content
		// Show if: media bar is focused OR (we're at position 0 AND media bar is enabled) OR position is -1 (toolbar/no selection)
		val isOnMediaBar = isFocused || (selectedPosition == 0 && isMediaBarEnabled) || selectedPosition == -1

		Timber.d("updateLogoVisibility: position=%d, isFocused=%b, isOnMediaBar=%b, logoUrl=%s",
			selectedPosition, isFocused, isOnMediaBar, selectedItemState?.logoUrl?.take(50))

		if (isOnMediaBar) {
			// On media bar - logo is shown in MediaBarSlideshowView, hide the fragment_home views
			logoView?.isVisible = false
			nameView?.isVisible = false
			titleView?.isVisible = false
		} else {
			// On other rows (Continue Watching, etc.)
			val hasLogo = selectedItemState?.logoUrl != null
			val hasTitle = !selectedItemState?.title.isNullOrEmpty()

			// Logo and name are mutually exclusive - show logo if available, otherwise name
			logoView?.isVisible = hasLogo
			nameView?.isVisible = !hasLogo

			// Title is always shown if present (e.g., episode title shown above series logo)
			titleView?.isVisible = hasTitle
		}
	}

	private fun updateDetailsVisibility(position: Int) {
		val isMediaBarEnabled = userSettingPreferences.activeHomesections.contains(org.jellyfin.androidtv.constant.HomeSectionType.MEDIA_BAR)
		val shouldShowDetails = position <= 0 || (position == 1 && !isMediaBarEnabled)

		// Animate details widgets (title/logo/name, infoRow, summary)
		val targetAlpha = if (shouldShowDetails) 1f else 0f
		val duration = 200L

		titleView?.animate()
			?.alpha(targetAlpha)
			?.setDuration(duration)
			?.start()

		nameView?.animate()
			?.alpha(targetAlpha)
			?.setDuration(duration)
			?.start()

		logoView?.animate()
			?.alpha(targetAlpha)
			?.setDuration(duration)
			?.start()

		infoRowView?.animate()
			?.alpha(targetAlpha)
			?.setDuration(duration)
			?.start()

		summaryView?.animate()
			?.alpha(targetAlpha)
			?.setDuration(duration)
			?.start()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		titleView = null
		nameView = null
		logoView = null
		summaryView = null
		infoRowView = null
		backgroundImage = null
		rowsFragment = null
		rowsFragmentState.value = null
	}
}
