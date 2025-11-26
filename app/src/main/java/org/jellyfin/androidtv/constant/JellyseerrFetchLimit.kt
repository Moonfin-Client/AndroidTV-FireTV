package org.jellyfin.androidtv.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

/**
 * Fetch limit options for Jellyseerr API requests
 * Allows users to configure the number of items to fetch per request
 */
enum class JellyseerrFetchLimit(
	override val nameRes: Int,
	val limit: Int,
) : PreferenceEnum {
	/**
	 * Small batch size (25 items) - good for slower connections
	 */
	SMALL(R.string.jellyseerr_fetch_limit_25, 25),

	/**
	 * Medium batch size (50 items) - balanced default
	 */
	MEDIUM(R.string.jellyseerr_fetch_limit_50, 50),

	/**
	 * Large batch size (75 items) - for faster connections
	 */
	LARGE(R.string.jellyseerr_fetch_limit_75, 75),
}
