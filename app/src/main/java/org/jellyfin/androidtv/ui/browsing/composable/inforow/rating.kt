package org.jellyfin.androidtv.ui.browsing.composable.inforow

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.MdbListRepository
import org.jellyfin.androidtv.data.repository.RatingIconProvider
import org.jellyfin.androidtv.data.repository.TmdbRepository
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.compose.koinInject
import timber.log.Timber
import java.text.NumberFormat

@Composable
private fun RatingItemWithLogo(
	icon: RatingIconProvider.RatingIcon,
	contentDescription: String,
	rating: String
) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.background(
				Color.White.copy(alpha = 0.1f),
				RoundedCornerShape(6.dp),
			)
			.padding(horizontal = 10.dp, vertical = 6.dp),
	) {
		RatingIconImage(icon = icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
		Column {
			Text(rating, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.W700)
			Text(contentDescription, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
		}
	}
}

@Composable
private fun RatingIconImage(
	icon: RatingIconProvider.RatingIcon,
	contentDescription: String,
	modifier: Modifier = Modifier,
) {
	when (icon) {
		is RatingIconProvider.RatingIcon.ServerUrl -> AsyncImage(
			model = icon.url,
			contentDescription = contentDescription,
			modifier = modifier,
		)
		is RatingIconProvider.RatingIcon.LocalDrawable -> Image(
			painter = painterResource(icon.resId),
			contentDescription = contentDescription,
			modifier = modifier,
		)
	}
}

/**
 * Display multiple ratings based on user's enabled rating types.
 */
@Composable
fun InfoRowMultipleRatings(item: BaseItemDto) {
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	val mdbListRepository = koinInject<MdbListRepository>()
	val tmdbRepository = koinInject<TmdbRepository>()
	val apiClient = koinInject<ApiClient>()
	val baseUrl = apiClient.baseUrl
	val enableAdditionalRatings = userSettingPreferences[UserSettingPreferences.enableAdditionalRatings]
	val enableEpisodeRatings = userSettingPreferences[UserSettingPreferences.enableEpisodeRatings]

	var apiRatings by remember { mutableStateOf<Map<String, Float>?>(null) }
	var isLoading by remember { mutableStateOf(false) }
	var episodeRating by remember { mutableStateOf<Float?>(null) }
	var isLoadingEpisode by remember { mutableStateOf(false) }
	var seriesCommunityRating by remember { mutableStateOf<Float?>(null) }

	val isEpisode = item.type == BaseItemKind.EPISODE

	// Fetch episode rating from TMDB if enabled
	if (enableEpisodeRatings && isEpisode) {
		LaunchedEffect(item.id) {
			isLoadingEpisode = true
			try {
				episodeRating = tmdbRepository.getEpisodeRating(item)
			} catch (e: Exception) {
				Timber.e(e, "Failed to fetch episode rating for item ${item.id}")
			} finally {
				isLoadingEpisode = false
			}
		}
	}

	if (isEpisode && item.communityRating == null) {
		LaunchedEffect(item.seriesId) {
			try {
				seriesCommunityRating = tmdbRepository.getSeriesCommunityRating(item)
			} catch (e: Exception) {
				Timber.e(e, "Failed to fetch series community rating for item ${item.id}")
			}
		}
	}

	if (enableAdditionalRatings) {
		LaunchedEffect(item.id) {
			isLoading = true
			try {
				apiRatings = mdbListRepository.getRatings(item)
			} catch (e: Exception) {
				Timber.e(e, "Failed to fetch MDBList ratings for item ${item.id}")
			} finally {
				isLoading = false
			}
		}
	}

	val allRatings = remember(apiRatings, item.criticRating, item.communityRating, episodeRating, seriesCommunityRating) {
		buildMap {
			episodeRating?.let { put("tmdb_episode", it / 10f) }

			// Jellyfin-provided critic rating (typically RT from OMDb)
			item.criticRating?.let { put("tomatoes", it / 100f) }

			// Normalize MDBList ratings to 0–1
			apiRatings?.forEach { (source, value) ->
				val normalized = when (source) {
					"tomatoes" -> item.criticRating?.let { it / 100f } ?: (value / 100f)
					"popcorn" -> value / 100f
					"imdb" -> value / 10f
					"tmdb" -> value / 100f
					"metacritic" -> value / 100f
					"metacriticuser" -> value / 100f
					"trakt" -> value / 100f
					"letterboxd" -> value / 5f
					"rogerebert" -> value / 4f
					"myanimelist" -> value / 10f
					"anilist" -> value / 100f
					else -> value / 10f
				}
				put(source, normalized)
			}
		}
	}

	if ((enableAdditionalRatings && isLoading) ||
		(enableEpisodeRatings && isEpisode && isLoadingEpisode)) {
		return
	}

	@OptIn(ExperimentalLayoutApi::class)
	FlowRow(
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		// Show TMDB episode rating
		if (enableEpisodeRatings && isEpisode && episodeRating != null) {
			RatingDisplay("tmdb_episode", allRatings["tmdb_episode"]!!, baseUrl)
		}

		allRatings["tomatoes"]?.let { RatingDisplay("tomatoes", it, baseUrl) }

		if (enableAdditionalRatings) {
			apiRatings?.keys?.forEach { source ->
				if (source == "tomatoes") return@forEach
				if (isEpisode && enableEpisodeRatings && source == "tmdb" && episodeRating != null) {
					return@forEach
				}
				allRatings[source]?.let { RatingDisplay(source, it, baseUrl) }
			}
		}
	}
}

/**
 * Display a single rating with icon and formatted value.
 * All rating values are expected in 0–1 normalized scale.
 */
@Composable
private fun RatingDisplay(sourceKey: String, rating: Float, baseUrl: String?) {
	val scorePercent = (rating * 100f).toInt()

	val icon = RatingIconProvider.getIcon(baseUrl, sourceKey, scorePercent) ?: return
	val formattedRating = when (sourceKey) {
		"tomatoes", "popcorn" -> NumberFormat.getPercentInstance().format(rating)
		"tmdb", "tmdb_episode", "metacritic", "metacriticuser", "trakt", "anilist" -> "${(rating * 100f).toInt()}%"
		"letterboxd" -> String.format("%.1f", rating * 5f)
		"rogerebert" -> String.format("%.1f", rating * 4f)
		else -> String.format("%.1f", rating * 10f)
	}
	val label = when (sourceKey) {
		"tomatoes" -> "Rotten Tomatoes"
		"popcorn" -> "RT Audience"
		"imdb" -> "IMDB"
		"tmdb", "tmdb_episode" -> "TMDB"
		"metacritic" -> "Metacritic"
		"metacriticuser" -> "Metacritic User"
		"trakt" -> "Trakt"
		"letterboxd" -> "Letterboxd"
		"rogerebert" -> "Roger Ebert"
		"myanimelist" -> "MyAnimeList"
		"anilist" -> "AniList"
		else -> sourceKey
	}
	RatingItemWithLogo(icon, label, formattedRating)
}

/**
 * A parental rating item in the [BaseItemInfoRow].
 */
@Composable
fun InfoRowParentalRating(parentalRating: String) {
	InfoRowItem(
		contentDescription = stringResource(R.string.lbl_rating),
		colors = InfoRowColors.Default,
	) {
		Text(parentalRating, color = Color.White)
	}
}
