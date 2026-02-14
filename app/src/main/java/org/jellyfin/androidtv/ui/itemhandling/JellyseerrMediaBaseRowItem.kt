package org.jellyfin.androidtv.ui.itemhandling

import android.content.Context
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.androidtv.util.apiclient.JellyfinImageSource
import java.util.UUID

/**
 * BaseRowItem wrapper for Jellyseerr media items (movies/TV shows).
 * Uses TMDB poster URLs directly via the JellyfinImage tag bypass.
 */
class JellyseerrMediaBaseRowItem(
	val item: JellyseerrDiscoverItemDto,
) : BaseRowItem(
	baseRowType = BaseRowType.BaseItem,
	staticHeight = true,
) {
	override fun getImage(imageType: ImageType): JellyfinImage? {
		val posterPath = item.posterPath ?: return null
		val tmdbUrl = "https://image.tmdb.org/t/p/w500$posterPath"
		return JellyfinImage(
			item = UUID(0, item.id.toLong()),
			source = JellyfinImageSource.ITEM,
			type = org.jellyfin.sdk.model.api.ImageType.PRIMARY,
			tag = tmdbUrl,
			blurHash = null,
			aspectRatio = ImageHelper.ASPECT_RATIO_2_3.toFloat(),
			index = null,
		)
	}

	override fun getCardName(context: Context): String? = item.title ?: item.name

	override fun getFullName(context: Context): String? = item.title ?: item.name

	override fun getName(context: Context): String? = item.title ?: item.name

	override fun getSubText(context: Context): String? {
		val year = item.releaseDate?.take(4) ?: item.firstAirDate?.take(4)
		return year ?: when (item.mediaType) {
			"movie" -> "Movie"
			"tv" -> "TV Series"
			else -> null
		}
	}

	override fun getSummary(context: Context): String? = item.overview
}
