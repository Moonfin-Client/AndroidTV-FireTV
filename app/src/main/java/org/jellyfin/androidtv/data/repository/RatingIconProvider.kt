package org.jellyfin.androidtv.data.repository

import org.jellyfin.androidtv.R

object RatingIconProvider {

	sealed class RatingIcon {
		data class ServerUrl(val url: String) : RatingIcon()
		data class LocalDrawable(val resId: Int) : RatingIcon()
	}

	fun getIcon(baseUrl: String?, source: String, scorePercent: Int? = null): RatingIcon? {
		if (baseUrl != null) {
			getServerIconFile(source, scorePercent)?.let { file ->
				return RatingIcon.ServerUrl("$baseUrl/Moonfin/Assets/$file")
			}
		}
		return getLocalFallbackIcon(source, scorePercent)
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

	private fun getLocalFallbackIcon(source: String, scorePercent: Int?): RatingIcon? = when (source) {
		"RT", "tomatoes" -> when {
			scorePercent != null && scorePercent < 60 -> RatingIcon.LocalDrawable(R.drawable.ic_rt_rotten)
			else -> RatingIcon.LocalDrawable(R.drawable.ic_rt_fresh)
		}
		else -> null
	}
}
