package org.jellyfin.androidtv.ui.home

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.ui.home.mediabar.MediaBarSlideshowView
import org.jellyfin.androidtv.ui.home.mediabar.MediaBarSlideshowViewModel
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository

/**
 * Presenter for the Media Bar row.
 * This presenter creates a full-width Compose view for the slideshow.
 */
class MediaBarPresenter(
	private val viewModel: MediaBarSlideshowViewModel,
	private val navigationRepository: NavigationRepository
) : RowPresenter() {
	
	override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
		// Create a container with bottom margin to push the next row down
		val container = FrameLayout(parent.context).apply {
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			)
		}
		
		val composeView = ComposeView(parent.context).apply {
			layoutParams = FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			).apply {
				// Add bottom margin to push next row out of frame
				bottomMargin = 40 // 40dp margin to push Continue Watching further down
			}
			
			setContent {
				MediaBarSlideshowView(
					viewModel = viewModel,
					onItemClick = { item ->
						// Navigate to item details with serverId for multi-server support
						navigationRepository.navigate(Destinations.itemDetails(item.itemId, item.serverId))
					}
				)
			}
		}
		
		container.addView(composeView)
		
		return ViewHolder(container)
	}
	
	override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
		// Binding is handled by Compose state
	}
	
	override fun onUnbindRowViewHolder(vh: RowPresenter.ViewHolder) {
		// Cleanup is handled by Compose lifecycle
	}
	
	class ViewHolder(view: android.view.View) : RowPresenter.ViewHolder(view)
}
