package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

/**
 * Focus color preference controlling the border color for focused cards, posters, and icons.
 * Uses the same color options as the media bar overlay, with bright values suited for focus borders.
 */
enum class AppTheme(
	override val nameRes: Int,
	val colorValue: Long,
) : PreferenceEnum {
	WHITE(R.string.pref_focus_color_white, 0xFFFFFFFF),
	BLACK(R.string.pref_media_bar_color_black, 0xFF000000),
	GRAY(R.string.pref_media_bar_color_gray, 0xFF9E9E9E),
	DARK_BLUE(R.string.pref_media_bar_color_dark_blue, 0xFF42A5F5),
	PURPLE(R.string.pref_media_bar_color_purple, 0xFFAB47BC),
	TEAL(R.string.pref_media_bar_color_teal, 0xFF26A69A),
	NAVY(R.string.pref_media_bar_color_navy, 0xFF3F51B5),
	CHARCOAL(R.string.pref_media_bar_color_charcoal, 0xFF78909C),
	BROWN(R.string.pref_media_bar_color_brown, 0xFF8D6E63),
	DARK_RED(R.string.pref_media_bar_color_dark_red, 0xFFEF5350),
	DARK_GREEN(R.string.pref_media_bar_color_dark_green, 0xFF66BB6A),
	SLATE(R.string.pref_media_bar_color_slate, 0xFF90A4AE),
	INDIGO(R.string.pref_media_bar_color_indigo, 0xFF7986CB),
}
