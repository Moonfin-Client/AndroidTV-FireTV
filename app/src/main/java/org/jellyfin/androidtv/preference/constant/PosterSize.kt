package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

enum class PosterSize(
	override val nameRes: Int,
	val height: Int,
	val landscapeHeight: Int,
) : PreferenceEnum {
	SMALL(R.string.pref_poster_size_small, 120, 88),
	DEFAULT(R.string.pref_poster_size_default, 150, 110),
	LARGE(R.string.pref_poster_size_large, 180, 132),
	EXTRA_LARGE(R.string.pref_poster_size_extra_large, 210, 154),
}
