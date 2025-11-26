package org.jellyfin.androidtv.ui.jellyseerr

import android.content.Context
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrCastMemberDto
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowType
import org.jellyfin.androidtv.util.ImageHelper

class CastRowItem(
	private val cast: JellyseerrCastMemberDto,
) : BaseRowItem(
	baseRowType = BaseRowType.Person,
	staticHeight = true,
) {
	override fun getImageUrl(
		context: Context,
		imageHelper: ImageHelper,
		imageType: ImageType,
		fillWidth: Int,
		fillHeight: Int
	): String? {
		// TMDB profile image URL
		return cast.profilePath?.let {
			"https://image.tmdb.org/t/p/w185$it"
		}
	}

	override fun getFullName(context: Context) = cast.name
	override fun getName(context: Context) = cast.name
	override fun getSubText(context: Context) = cast.character
}
