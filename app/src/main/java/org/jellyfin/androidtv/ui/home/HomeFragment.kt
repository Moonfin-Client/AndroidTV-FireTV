package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.constant.NavbarPosition
import org.jellyfin.androidtv.ui.home.mediabar.MediaBarSlideshowViewModel
import org.jellyfin.androidtv.ui.home.mediabar.TrailerPreviewState
import org.jellyfin.androidtv.ui.home.mediabar.YouTubeTrailerWebView
import org.jellyfin.androidtv.ui.shared.toolbar.LeftSidebarNavigation
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.android.ext.android.inject

class HomeFragment : Fragment() {
	private val mediaBarViewModel by inject<MediaBarSlideshowViewModel>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val userPreferences by inject<UserPreferences>()

	private var titleView: TextView? = null
	private var logoView: ImageView? = null
	private var infoRowView: SimpleInfoRowView? = null
	private var summaryView: TextView? = null
	private var backgroundImage: ImageView? = null
	private var trailerWebView: ComposeView? = null
	private var rowsFragment: HomeRowsFragment? = null
	private var snowfallView: SnowfallView? = null
	private var petalfallView: PetalfallView? = null
	private var leaffallView: LeaffallView? = null
	private var summerView: SummerView? = null
	private var halloweenView: HalloweenView? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val view = inflater.inflate(R.layout.fragment_home, container, false)

		titleView = view.findViewById(R.id.title)
		logoView = view.findViewById(R.id.logo)
		infoRowView = view.findViewById(R.id.infoRow)
		summaryView = view.findViewById(R.id.summary)
		backgroundImage = view.findViewById(R.id.backgroundImage)
		trailerWebView = view.findViewById(R.id.trailerWebView)
		snowfallView = view.findViewById(R.id.snowfallView)
		petalfallView = view.findViewById(R.id.petalfallView)
		leaffallView = view.findViewById(R.id.leaffallView)
		summerView = view.findViewById(R.id.summerView)
		halloweenView = view.findViewById(R.id.halloweenView)

		val navbarPosition = userPreferences[UserPreferences.navbarPosition] ?: NavbarPosition.TOP
		
		when (navbarPosition) {
			NavbarPosition.LEFT -> {
				val toolbarContainer = view.findViewById<FrameLayout>(R.id.toolbar_actions)
				toolbarContainer.isVisible = false
				
				val sidebarContainer = view.findViewById<FrameLayout>(R.id.left_sidebar)
				sidebarContainer.isVisible = true
				
				val sidebarView = view.findViewById<ComposeView>(R.id.sidebar)
				sidebarView.setContent {
					LeftSidebarNavigation(
						activeButton = MainToolbarActiveButton.Home
					)
				}
			}
			NavbarPosition.TOP -> {
				val sidebarContainer = view.findViewById<FrameLayout>(R.id.left_sidebar)
				sidebarContainer.isVisible = false
				
				val toolbarContainer = view.findViewById<FrameLayout>(R.id.toolbar_actions)
				toolbarContainer.isVisible = true
				
				val toolbarView = view.findViewById<ComposeView>(R.id.toolbar)
				toolbarView.setContent {
					MainToolbar(
						activeButton = MainToolbarActiveButton.Home
					)
				}
			}
		}

		return view
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		setupSeasonalSurprise()

		rowsFragment = childFragmentManager.findFragmentById(R.id.rowsFragment) as? HomeRowsFragment

		rowsFragment?.selectedItemStateFlow
			?.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			?.onEach { state ->
				titleView?.text = state.title
				summaryView?.text = state.summary
				infoRowView?.setItem(state.baseItem)
			}
			?.launchIn(lifecycleScope)

		rowsFragment?.selectedPositionFlow
			?.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			?.onEach { position ->
				updateMediaBarBackground()
			}
			?.launchIn(lifecycleScope)

		mediaBarViewModel.state
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { state ->
				updateMediaBarBackground()
			}
			.launchIn(lifecycleScope)

		mediaBarViewModel.isFocused
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { isFocused ->
				updateMediaBarBackground()
			}
			.launchIn(lifecycleScope)

		mediaBarViewModel.playbackState
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach {
				updateMediaBarBackground()
			}
			.launchIn(lifecycleScope)

		trailerWebView?.setContent {
			val trailerState by mediaBarViewModel.trailerState.collectAsState()
			val previewAudioEnabled = remember { userSettingPreferences[UserSettingPreferences.previewAudioEnabled] }

			val activeInfo = when (val state = trailerState) {
				is TrailerPreviewState.Buffering -> state.info
				is TrailerPreviewState.Playing -> state.info
				else -> null
			}
			val showTrailer = trailerState is TrailerPreviewState.Playing

			if (activeInfo != null) {
				key(activeInfo.youtubeVideoId) {
					YouTubeTrailerWebView(
						videoId = activeInfo.youtubeVideoId,
						startSeconds = activeInfo.startSeconds,
						segments = activeInfo.segments,
						muted = !previewAudioEnabled,
						isVisible = showTrailer,
						onVideoEnded = { mediaBarViewModel.onTrailerEnded() },
						onVideoReady = { mediaBarViewModel.onTrailerReady() },
					)
				}
			}
		}

		mediaBarViewModel.trailerState
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { trailerState ->
				val hasTrailer = trailerState is TrailerPreviewState.Buffering ||
					trailerState is TrailerPreviewState.Playing
				trailerWebView?.isVisible = hasTrailer
			}
			.launchIn(lifecycleScope)
	}

	private fun updateMediaBarBackground() {
		val state = mediaBarViewModel.state.value
		val isFocused = mediaBarViewModel.isFocused.value
		val selectedPosition = rowsFragment?.selectedPositionFlow?.value ?: -1
		
		val isMediaBarEnabled = userSettingPreferences[UserSettingPreferences.mediaBarEnabled]
		val shouldShowMediaBar = isMediaBarEnabled && (isFocused || (selectedPosition == 0) || selectedPosition == -1)
		
		if (state is org.jellyfin.androidtv.ui.home.mediabar.MediaBarState.Ready && shouldShowMediaBar) {
			val playbackState = mediaBarViewModel.playbackState.value
			val currentItem = state.items.getOrNull(playbackState.currentIndex)
			val backdropUrl = currentItem?.backdropUrl
			
			if (backdropUrl != null) {
				backgroundImage?.isVisible = true
				backgroundImage?.load(backdropUrl) {
					crossfade(400) // 400ms crossfade - faster and smoother
				}
			} else {
				backgroundImage?.isVisible = false
			}
			
			logoView?.isVisible = false
			titleView?.isVisible = false
		} else {
			backgroundImage?.isVisible = false
			logoView?.isVisible = false
			titleView?.isVisible = true
		}
	}

	/**
	 * Setup the seasonal surprise effects based on user selection.
	 * Options: none, winter (❄️), spring (🌸🌼), summer (☀️🏐), fall (🍁🍂)
	 */
	private fun setupSeasonalSurprise() {
		val selection = userPreferences[UserPreferences.seasonalSurprise]

		snowfallView?.isVisible = false
		snowfallView?.stopSnowing()
		petalfallView?.isVisible = false
		petalfallView?.stopFalling()
		leaffallView?.isVisible = false
		leaffallView?.stopFalling()
		summerView?.isVisible = false
		summerView?.stopEffect()
		halloweenView?.isVisible = false
		halloweenView?.stopEffect()
		
		when (selection) {
			"winter" -> {
				snowfallView?.isVisible = true
				snowfallView?.startSnowing()
			}
			"spring" -> {
				petalfallView?.isVisible = true
				petalfallView?.startFalling()
			}
			"summer" -> {
				summerView?.isVisible = true
				summerView?.startEffect()
			}
			"halloween" -> {
				halloweenView?.isVisible = true
				halloweenView?.startEffect()
			}
			"fall" -> {
				leaffallView?.isVisible = true
				leaffallView?.startFalling()
			}
			// "none" or any other value - no effect
		}
	}

	override fun onPause() {
		super.onPause()
		mediaBarViewModel.stopTrailer()
	}

	override fun onResume() {
		super.onResume()
		mediaBarViewModel.restartTrailerForCurrentSlide()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		snowfallView?.stopSnowing()
		petalfallView?.stopFalling()
		leaffallView?.stopFalling()
		summerView?.stopEffect()
		halloweenView?.stopEffect()
		titleView = null
		logoView = null
		summaryView = null
		infoRowView = null
		backgroundImage = null
		trailerWebView = null
		rowsFragment = null
		snowfallView = null
		petalfallView = null
		leaffallView = null
		summerView = null
		halloweenView = null
	}
}
