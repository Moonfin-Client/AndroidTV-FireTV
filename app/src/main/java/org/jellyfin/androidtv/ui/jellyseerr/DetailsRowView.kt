package org.jellyfin.androidtv.ui.jellyseerr

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import org.jellyfin.androidtv.databinding.ViewJellyseerrDetailsRowBinding

class DetailsRowView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
	defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
	val binding = ViewJellyseerrDetailsRowBinding.inflate(LayoutInflater.from(context), this, true)
}
