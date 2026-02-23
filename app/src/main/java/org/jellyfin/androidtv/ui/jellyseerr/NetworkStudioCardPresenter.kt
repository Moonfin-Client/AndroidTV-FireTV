package org.jellyfin.androidtv.ui.jellyseerr

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.leanback.widget.Presenter
import coil3.load
import coil3.request.crossfade
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrNetworkDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrStudioDto
import org.jellyfin.androidtv.preference.UserPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Presenter for network and studio cards with logo display
 * Uses pre-filtered duotone URLs from Seerr for consistent white appearance
 */
class NetworkStudioCardPresenter : Presenter(), KoinComponent {
	private val userPreferences by inject<UserPreferences>()
	private val ASPECT_RATIO = 2f // landscape 2:1

	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val view = LayoutInflater.from(parent.context)
			.inflate(R.layout.jellyseerr_network_studio_card, parent, false) as FrameLayout

		return ViewHolder(view)
	}

	override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
		val container = viewHolder.view as FrameLayout
		val logoView = container.findViewById<ImageView>(R.id.logo_image)

		// Set card size to match poster height preference with landscape aspect ratio
		val posterHeight = userPreferences[UserPreferences.posterSize].height
		val density = container.context.resources.displayMetrics.density
		val heightPx = (posterHeight * density).toInt()
		val widthPx = (posterHeight * ASPECT_RATIO * density).toInt()
		val layoutParams = container.layoutParams
		layoutParams.width = widthPx
		layoutParams.height = heightPx
		container.layoutParams = layoutParams

		when (item) {
			is JellyseerrNetworkDto -> {
				Timber.d("NetworkStudioCardPresenter: Binding network ${item.name} with logo ${item.logoPath}")
				loadLogo(logoView, item.logoPath, item.name)
			}
			is JellyseerrStudioDto -> {
				Timber.d("NetworkStudioCardPresenter: Binding studio ${item.name} with logo ${item.logoPath}")
				loadLogo(logoView, item.logoPath, item.name)
			}
			else -> {
				Timber.w("NetworkStudioCardPresenter: Unknown item type: ${item?.javaClass?.simpleName}")
				logoView.setImageDrawable(null)
			}
		}
	}

	private fun loadLogo(logoView: ImageView, logoUrl: String?, name: String) {
		if (!logoUrl.isNullOrEmpty()) {
			logoView.load(logoUrl) {
				crossfade(true)
				listener(
					onSuccess = { _, _ ->
						Timber.d("NetworkStudioCardPresenter: Successfully loaded logo for $name")
					},
					onError = { _, result ->
						Timber.e("NetworkStudioCardPresenter: Failed to load logo for $name: ${result.throwable.message}")
					}
				)
			}
		} else {
			Timber.w("NetworkStudioCardPresenter: No logo URL for $name")
			logoView.setImageDrawable(null)
		}
	}

	override fun onUnbindViewHolder(viewHolder: ViewHolder) {
		val container = viewHolder.view as FrameLayout
		val logoView = container.findViewById<ImageView>(R.id.logo_image)
		logoView.setImageDrawable(null)
	}
}
