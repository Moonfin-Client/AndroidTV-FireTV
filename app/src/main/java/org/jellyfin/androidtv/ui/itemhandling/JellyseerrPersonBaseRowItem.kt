package org.jellyfin.androidtv.ui.itemhandling

import android.content.Context
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrCastMemberDto
import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.androidtv.util.apiclient.JellyfinImageSource
import java.util.UUID

/**
 * BaseRowItem wrapper for Jellyseerr cast members.
 * Renders as a circular person card using TMDB profile URLs.
 */
class JellyseerrPersonBaseRowItem(
	val castMember: JellyseerrCastMemberDto,
) : BaseRowItem(
	baseRowType = BaseRowType.Person,
	staticHeight = true,
) {
	override fun getImage(imageType: ImageType): JellyfinImage? {
		val profilePath = castMember.profilePath ?: return null
		val tmdbUrl = "https://image.tmdb.org/t/p/w185$profilePath"
		return JellyfinImage(
			item = UUID(0, castMember.id.toLong()),
			source = JellyfinImageSource.ITEM,
			type = org.jellyfin.sdk.model.api.ImageType.PRIMARY,
			tag = tmdbUrl,
			blurHash = null,
			aspectRatio = 1f,
			index = null,
		)
	}

	override fun getCardName(context: Context): String? = castMember.name

	override fun getFullName(context: Context): String? = castMember.name

	override fun getName(context: Context): String? = castMember.name

	override fun getSubText(context: Context): String? = castMember.character
}
