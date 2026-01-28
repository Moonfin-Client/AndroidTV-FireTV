package org.jellyfin.androidtv.ui.browsing.composable.inforow

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.MdbListRepository
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.constant.RatingType
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.compose.koinInject
import java.text.NumberFormat

@Composable
private fun RatingItemWithLogo(
	logoRes: Int,
	contentDescription: String,
	rating: String
) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(3.dp),
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier.fillMaxHeight(),
	) {
		Image(
			painter = painterResource(logoRes),
			contentDescription = contentDescription,
			modifier = Modifier.size(18.dp)
		)
		Text(rating, color = Color.White)
	}
}

/**
 * A community rating item in the [BaseItemInfoRow].
 *
 * @param communityRating Between 0f and 1f.
 */
@Composable
fun InfoRowCommunityRating(communityRating: Float) {
	InfoRowItem(
		icon = ImageVector.vectorResource(R.drawable.ic_star),
		iconTint = Color(0xFFEECE55),
		contentDescription = stringResource(R.string.lbl_community_rating),
	) {
		Text(String.format("%.1f", communityRating * 10f), color = Color.White)
	}
}

private const val CRITIC_RATING_FRESH = 0.6f

/**
 * A critic rating item in the [BaseItemInfoRow].
 *
 * @param criticRating Between 0f and 1f.
 */
@Composable
fun InfoRowCriticRating(criticRating: Float) {
	InfoRowItem(
		icon = when {
			criticRating >= CRITIC_RATING_FRESH -> ImageVector.vectorResource(R.drawable.ic_rt_fresh)
			else -> ImageVector.vectorResource(R.drawable.ic_rt_rotten)
		},
		iconTint = Color.Unspecified,
		contentDescription = stringResource(R.string.lbl_critic_rating),
	) {
		Text(NumberFormat.getPercentInstance().format(criticRating), color = Color.White)
	}
}

/**
 * Display multiple ratings based on user's enabled rating types.
 * Shows all enabled ratings in a row.
 */
@Composable
fun InfoRowMultipleRatings(item: BaseItemDto) {
	val context = LocalContext.current
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	val mdbListRepository = koinInject<MdbListRepository>()
	val enableAdditionalRatings = userSettingPreferences[UserSettingPreferences.enableAdditionalRatings]
	val apiKey = userSettingPreferences[UserSettingPreferences.mdblistApiKey]
	val enabledRatingsStr = userSettingPreferences[UserSettingPreferences.enabledRatings]

	val enabledRatings = remember(enabledRatingsStr) {
		enabledRatingsStr.split(",")
			.filter { it.isNotBlank() }
			.mapNotNull { name -> RatingType.entries.find { it.name == name } }
			.filter { it != RatingType.RATING_HIDDEN }
			.toSet()
	}

	if (enabledRatings.isEmpty()) return

	var hasShownToast by remember { mutableStateOf(false) }
	var apiRatings by remember { mutableStateOf<Map<String, Float>?>(null) }
	var isLoading by remember { mutableStateOf(false) }

	val needsApiRatings = enabledRatings.any { 
		it !in listOf(RatingType.RATING_TOMATOES, RatingType.RATING_STARS) 
	}

	if (enableAdditionalRatings && needsApiRatings) {
		LaunchedEffect(item.id, apiKey) {
			if (apiKey.isBlank()) {
				if (!hasShownToast) {
					Toast.makeText(
						context,
						context.getString(R.string.pref_additional_ratings_no_api_key),
						Toast.LENGTH_LONG
					).show()
					hasShownToast = true
				}
				return@LaunchedEffect
			}
			isLoading = true
			try {
				apiRatings = mdbListRepository.getRatings(item, apiKey)
			} catch (e: Exception) {
			} finally {
				isLoading = false
			}
		}
	}

	val allRatings = remember(apiRatings, item.criticRating, item.communityRating) {
		buildMap {
			item.criticRating?.let { put("RT", it / 100f) }
			apiRatings?.get("tomatoes_audience")?.let { put("RT_AUDIENCE", it / 100f) }
			item.communityRating?.let { put("STARS", it / 10f) }

			apiRatings?.let { ratings ->
				ratings["imdb"]?.let { put("imdb", it / 10f) }
				ratings["tmdb"]?.let { put("tmdb", it / 100f) }
				ratings["metacritic"]?.let { put("metacritic", it / 100f) }
				ratings["trakt"]?.let { put("trakt", it / 100f) }
				ratings["letterboxd"]?.let { put("letterboxd", it / 5f) }
				ratings["rogerebert"]?.let { put("rogerebert", it / 4f) }
				ratings["myanimelist"]?.let { put("myanimelist", it / 10f) }
				ratings["anilist"]?.let { put("anilist", it / 100f) }
				ratings["kinopoisk"]?.let { put("kinopoisk", it / 10f) }
				ratings["allocine"]?.let { put("allocine", it / 5f) }
				ratings["douban"]?.let { put("douban", it / 10f) }
			}
		}
	}

	if (needsApiRatings && enableAdditionalRatings && isLoading && apiKey.isNotBlank()) {
		return
	}

	Row(
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		enabledRatings.forEach { ratingType ->
			val (sourceKey, rating) = when (ratingType) {
				RatingType.RATING_TOMATOES -> "RT" to allRatings["RT"]
				RatingType.RATING_RT_AUDIENCE -> "RT_AUDIENCE" to allRatings["RT_AUDIENCE"]
				RatingType.RATING_STARS -> "STARS" to allRatings["STARS"]
				RatingType.RATING_IMDB -> "imdb" to allRatings["imdb"]
				RatingType.RATING_TMDB -> "tmdb" to allRatings["tmdb"]
				RatingType.RATING_METACRITIC -> "metacritic" to allRatings["metacritic"]
				RatingType.RATING_TRAKT -> "trakt" to allRatings["trakt"]
				RatingType.RATING_LETTERBOXD -> "letterboxd" to allRatings["letterboxd"]
				RatingType.RATING_ROGER_EBERT -> "rogerebert" to allRatings["rogerebert"]
				RatingType.RATING_MYANIMELIST -> "myanimelist" to allRatings["myanimelist"]
				RatingType.RATING_ANILIST -> "anilist" to allRatings["anilist"]
				RatingType.RATING_KINOPOISK -> "kinopoisk" to allRatings["kinopoisk"]
				RatingType.RATING_ALLOCINE -> "allocine" to allRatings["allocine"]
				RatingType.RATING_DOUBAN -> "douban" to allRatings["douban"]
				RatingType.RATING_HIDDEN -> return@forEach
			}
			
			rating?.let { value ->
				RatingDisplay(sourceKey, value)
			}
		}
	}
}

/**
 * Display a single rating with appropriate icon and formatting
 */
@Composable
private fun RatingDisplay(sourceKey: String, rating: Float) {
	when (sourceKey) {
		"RT" -> InfoRowItem(
			icon = when {
				rating >= CRITIC_RATING_FRESH -> ImageVector.vectorResource(R.drawable.ic_rt_fresh)
				else -> ImageVector.vectorResource(R.drawable.ic_rt_rotten)
			},
			iconTint = Color.Unspecified,
			contentDescription = "Rotten Tomatoes",
		) {
			Text(NumberFormat.getPercentInstance().format(rating), color = Color.White)
		}
		"RT_AUDIENCE" -> InfoRowItem(
			icon = when {
				rating >= CRITIC_RATING_FRESH -> ImageVector.vectorResource(R.drawable.ic_rt_audience_fresh)
				else -> ImageVector.vectorResource(R.drawable.ic_rt_audience_rotten)
			},
			iconTint = Color.Unspecified,
			contentDescription = "RT Audience",
		) {
			Text(NumberFormat.getPercentInstance().format(rating), color = Color.White)
		}
		"STARS" -> InfoRowCommunityRating(rating)
		"imdb" -> RatingItemWithLogo(R.drawable.ic_imdb, "IMDB", String.format("%.1f", rating * 10f))
		"tmdb" -> RatingItemWithLogo(R.drawable.ic_tmdb, "TMDB", (rating * 100f).toInt().toString())
		"metacritic" -> RatingItemWithLogo(R.drawable.ic_metacritic, "Metacritic", (rating * 100f).toInt().toString())
		"trakt" -> RatingItemWithLogo(R.drawable.ic_trakt, "Trakt", (rating * 100f).toInt().toString() + "%")
		"letterboxd" -> RatingItemWithLogo(R.drawable.ic_letterboxd, "Letterboxd", String.format("%.1f", rating * 5f))
		"rogerebert" -> RatingItemWithLogo(R.drawable.ic_roger_ebert, "Roger Ebert", String.format("%.1f", rating * 4f))
		"myanimelist" -> RatingItemWithLogo(R.drawable.ic_myanimelist, "MyAnimeList", String.format("%.1f", rating * 10f))
		"anilist" -> RatingItemWithLogo(R.drawable.ic_anilist, "AniList", (rating * 100f).toInt().toString())
		"kinopoisk" -> RatingItemWithLogo(R.drawable.ic_kinopoisk, "Kinopoisk", String.format("%.1f", rating * 10f))
		"allocine" -> RatingItemWithLogo(R.drawable.ic_allocine, "AlloCiné", String.format("%.1f", rating * 5f))
		"douban" -> RatingItemWithLogo(R.drawable.ic_douban, "Douban", String.format("%.1f", rating * 10f))
	}
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
