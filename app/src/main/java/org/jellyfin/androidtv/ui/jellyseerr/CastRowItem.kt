package org.jellyfin.androidtv.ui.jellyseerr

import android.content.Context
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrCastMemberDto
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowType
import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.androidtv.util.apiclient.JellyfinImageSource
import java.util.UUID

class CastRowItem(
	private val cast: JellyseerrCastMemberDto,
) : BaseRowItem(
	baseRowType = BaseRowType.Person,
	staticHeight = true,
) {
	override fun getImage(imageType: ImageType): JellyfinImage? {
		val profileUrl = cast.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" } ?: return null
		return JellyfinImage(
			item = UUID.randomUUID(),
			source = JellyfinImageSource.ITEM,
			type = org.jellyfin.sdk.model.api.ImageType.PRIMARY,
			tag = profileUrl,
			blurHash = null,
			aspectRatio = 0.67f,
			index = null,
		)
	}

	override fun getFullName(context: Context) = cast.name
	override fun getName(context: Context) = cast.name
	override fun getSubText(context: Context) = cast.character
}
