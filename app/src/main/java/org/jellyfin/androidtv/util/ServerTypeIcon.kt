package org.jellyfin.androidtv.util

import android.widget.TextView
import androidx.core.content.ContextCompat
import org.jellyfin.androidtv.R
import org.moonfin.server.core.model.ServerType

fun TextView.setServerTypeIcon(serverType: ServerType, sizeDp: Int = 18, paddingDp: Int = 8) {
	val density = context.resources.displayMetrics.density
	val iconRes = when (serverType) {
		ServerType.JELLYFIN -> R.drawable.ic_jellyfin
		ServerType.EMBY -> R.drawable.ic_emby
	}
	val icon = ContextCompat.getDrawable(context, iconRes)?.apply {
		val size = (sizeDp * density).toInt()
		setBounds(0, 0, size, size)
	}
	setCompoundDrawablesRelative(icon, null, null, null)
	compoundDrawablePadding = (paddingDp * density).toInt()
}
