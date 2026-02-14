package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

enum class RatingType(
	override val nameRes: Int,
) : PreferenceEnum {
	RATING_TOMATOES(R.string.lbl_tomatoes),
	RATING_RT_AUDIENCE(R.string.pref_rating_source_rt_audience),
	RATING_STARS(R.string.lbl_stars),
	RATING_IMDB(R.string.pref_rating_source_imdb),
	RATING_TMDB(R.string.pref_rating_source_tmdb),
	RATING_METACRITIC(R.string.pref_rating_source_metacritic),
	RATING_METACRITIC_USER(R.string.pref_rating_source_metacritic_user),
	RATING_TRAKT(R.string.pref_rating_source_trakt),
	RATING_LETTERBOXD(R.string.pref_rating_source_letterboxd),
	RATING_ROGER_EBERT(R.string.pref_rating_source_rogerebert),
	RATING_MYANIMELIST(R.string.pref_rating_source_myanimelist),
	RATING_ANILIST(R.string.pref_rating_source_anilist),
	RATING_HIDDEN(R.string.lbl_hidden),
}

