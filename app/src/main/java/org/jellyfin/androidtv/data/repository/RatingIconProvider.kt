package org.jellyfin.androidtv.data.repository

/**
 * Resolves rating source icons from the Moonfin server plugin's
 * `/Moonfin/Assets/` endpoint.
 *
 * Icon variants are selected based on score:
 * - **RT Critic:** Certified Fresh (>=75%), Fresh (>=60%), Rotten (<60%)
 * - **RT Audience:** Verified Hot (>=90%), Upright Popcorn (>=60%), Spilled Popcorn (<60%)
 * - **Metacritic:** Score badge (>=81%), Generic logo (<81%)
 */
object RatingIconProvider {

	/** Either a server-hosted icon URL or a local drawable resource. */
	sealed class RatingIcon {
		data class ServerUrl(val url: String) : RatingIcon()
		data class LocalDrawable(val resId: Int) : RatingIcon()
	}

	/**
	 * Get the icon for a rating source.
	 *
	 * @param baseUrl Jellyfin server base URL (e.g. `http://192.168.1.1:8096`).
	 * @param source Rating source key (e.g. `"imdb"`, `"RT"`, `"popcorn"`).
	 * @param scorePercent Rating value as a 0–100 percentage for score-based icon
	 *   variant selection. Pass `null` to use the default icon for the source.
	 * @return A [RatingIcon] or `null` if no icon is available for this source.
	 */
	fun getIcon(baseUrl: String?, source: String, scorePercent: Int? = null): RatingIcon? {
		if (baseUrl != null) {
			getServerIconFile(source, scorePercent)?.let { file ->
				return RatingIcon.ServerUrl("$baseUrl/Moonfin/Assets/$file")
			}
		}
		return null
	}

	private fun getServerIconFile(source: String, scorePercent: Int?): String? = when (source) {
		"RT", "tomatoes" -> when {
			scorePercent != null && scorePercent >= 75 -> "rt-certified.svg"
			scorePercent != null && scorePercent < 60 -> "rt-rotten.svg"
			else -> "rt-fresh.svg"
		}
		"RT_AUDIENCE", "tomatoes_audience", "popcorn" -> when {
			scorePercent != null && scorePercent >= 90 -> "rt-verified.svg"
			scorePercent != null && scorePercent < 60 -> "rt-audience-down.svg"
			else -> "rt-audience-up.svg"
		}
		"metacritic" -> when {
			scorePercent != null && scorePercent >= 81 -> "metacritic-score.svg"
			else -> "metacritic.svg"
		}
		"metacriticuser" -> "metacritic-user.svg"
		"imdb" -> "imdb.svg"
		"tmdb", "tmdb_episode" -> "tmdb.svg"
		"trakt" -> "trakt.svg"
		"letterboxd" -> "letterboxd.svg"
		"rogerebert" -> "rogerebert.svg"
		"myanimelist" -> "mal.svg"
		"anilist" -> "anilist.svg"
		else -> null
	}
}
