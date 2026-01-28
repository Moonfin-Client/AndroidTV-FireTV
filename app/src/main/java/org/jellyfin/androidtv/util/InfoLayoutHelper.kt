package org.jellyfin.androidtv.util

import android.content.Context
import android.widget.LinearLayout
import org.jellyfin.androidtv.ui.browsing.composable.inforow.BaseItemInfoRowView
import org.jellyfin.androidtv.ui.browsing.composable.inforow.RatingsRowView
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo

object InfoLayoutHelper {
	@JvmStatic
	fun addInfoRow(
		context: Context,
		item: BaseItemDto?,
		mediaSource: MediaSourceInfo?,
		layout: LinearLayout,
		includeRuntime: Boolean
	) {
		var baseItemInfoRowView: BaseItemInfoRowView? = null

		for (i in 0 until layout.childCount) {
			val child = layout.getChildAt(i)

			if (child is BaseItemInfoRowView) {
				baseItemInfoRowView = child
				break
			}
		}

		if (baseItemInfoRowView == null) {
			baseItemInfoRowView = BaseItemInfoRowView(context)
			layout.addView(baseItemInfoRowView)
		}

		baseItemInfoRowView.item = item
		baseItemInfoRowView.mediaSource = mediaSource ?: item?.mediaSources?.firstOrNull()
		baseItemInfoRowView.includeRuntime = includeRuntime
	}

	@JvmStatic
	fun addInfoRow(
		context: Context,
		item: BaseItemDto?,
		layout: LinearLayout,
		includeRuntime: Boolean
	) = addInfoRow(context, item, null, layout, includeRuntime)

	@JvmStatic
	fun addRatingsRow(
		context: Context,
		item: BaseItemDto?,
		layout: LinearLayout
	) {
		var ratingsRowView: RatingsRowView? = null

		for (i in 0 until layout.childCount) {
			val child = layout.getChildAt(i)

			if (child is RatingsRowView) {
				ratingsRowView = child
				break
			}
		}

		if (ratingsRowView == null) {
			ratingsRowView = RatingsRowView(context)
			layout.addView(ratingsRowView)
		}

		ratingsRowView.item = item
	}
}
