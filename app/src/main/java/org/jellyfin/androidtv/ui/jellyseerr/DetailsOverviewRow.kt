package org.jellyfin.androidtv.ui.jellyseerr

import android.graphics.drawable.Drawable
import android.view.View
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrMovieDetailsDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrTvDetailsDto

class DetailsOverviewRow(
	val item: JellyseerrDiscoverItemDto,
	var imageDrawable: Drawable? = null,
	val actions: List<View> = emptyList(),
	val movieDetails: JellyseerrMovieDetailsDto? = null,
	val tvDetails: JellyseerrTvDetailsDto? = null,
) : Row()
