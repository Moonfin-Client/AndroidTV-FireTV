package org.jellyfin.androidtv.ui.home

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.MdbListRepository
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.constant.RatingType
import org.jellyfin.androidtv.ui.composable.getResolutionName
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Lightweight View-based info row that displays metadata without Compose overhead.
 * Updates are simple property assignments with no recomposition.
 */
class SimpleInfoRowView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : LinearLayout(context, attrs), KoinComponent {
	
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val mdbListRepository by inject<MdbListRepository>()
	private val items = mutableListOf<TextView>()
	private var currentItemId: String? = null
	
	init {
		orientation = HORIZONTAL
		gravity = Gravity.CENTER_VERTICAL
		
		// Pre-create a pool of TextViews for reuse (increased for more metadata + ratings)
		repeat(20) {
			val textView = TextView(context).apply {
				setTextColor(ContextCompat.getColor(context, android.R.color.white))
				setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
				typeface = Typeface.DEFAULT
				setShadowLayer(3f, 0f, 1f, ContextCompat.getColor(context, android.R.color.black))
				setPadding(0, 0, dpToPx(12), 0)
			}
			items.add(textView)
			addView(textView)
		}
	}
	
	fun setItem(item: BaseItemDto?) {
		// Hide all items first
		items.forEach { 
			it.visibility = GONE
			it.text = ""
			it.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
		}
		
		if (item == null) {
			currentItemId = null
			return
		}
		
		// Track current item to prevent updating wrong item after recycling
		currentItemId = item.id.toString()
		
		var index = 0
		
		// Date based on item type
		val dateText = when (item.type) {
			BaseItemKind.SERIES -> item.productionYear?.toString()
			BaseItemKind.EPISODE -> item.premiereDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
			else -> item.productionYear?.toString() 
				?: item.premiereDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
		}
		dateText?.let { setItemText(index++, it) }
		
		// Episode info (Season/Episode number)
		if (item.type == BaseItemKind.EPISODE) {
			val seasonNum = item.parentIndexNumber
			val episodeNum = item.indexNumber
			if (seasonNum != null && episodeNum != null) {
				setItemText(index++, "S${seasonNum}E${episodeNum}")
			}
		}
		
		// Official Rating (e.g., PG-13)
		item.officialRating?.let { rating ->
			if (rating.isNotBlank()) {
				setItemText(index++, rating)
			}
		}
		
		// Get media streams for detailed info
		val mediaSource = item.mediaSources?.firstOrNull()
		val videoStream = mediaSource?.mediaStreams?.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO }
		
		// Subtitle indicators
		val hasSdhSubtitles = mediaSource?.mediaStreams?.any { 
			it.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE && it.isHearingImpaired 
		} == true
		val hasCcSubtitles = mediaSource?.mediaStreams?.any { 
			it.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE && !it.isHearingImpaired 
		} == true
		
		if (hasSdhSubtitles) {
			setItemText(index++, "SDH")
		}
		if (hasCcSubtitles) {
			setItemText(index++, "CC")
		}
		
		// Video resolution
		if (videoStream?.width != null && videoStream.height != null) {
			val resolution = getResolutionName(
				context = context,
				width = videoStream.width!!,
				height = videoStream.height!!,
				interlaced = videoStream.isInterlaced
			)
			setItemText(index++, resolution)
		}
		
		// Parse enabled ratings from preferences
		val enabledRatingsStr = userSettingPreferences[UserSettingPreferences.enabledRatings]
		val enableAdditionalRatings = userSettingPreferences[UserSettingPreferences.enableAdditionalRatings]
		val enabledRatings = enabledRatingsStr.split(",")
			.filter { it.isNotBlank() }
			.mapNotNull { name -> RatingType.entries.find { it.name == name } }
			.filter { it != RatingType.RATING_HIDDEN }
		
		if (enabledRatings.isEmpty()) return
		
		// If additional ratings enabled, show all ratings at the end
		if (enableAdditionalRatings) {
			index = showAllRatings(item, enabledRatings, index)
		} else {
			// Show only first rating
			val firstEnabledRating = enabledRatings.first()
			index = showSingleRating(item, firstEnabledRating, index, enableAdditionalRatings)
		}
	}
	
	private fun showAllRatings(item: BaseItemDto, enabledRatings: List<RatingType>, startIndex: Int): Int {
		val apiKey = userSettingPreferences[UserSettingPreferences.mdblistApiKey]
		val itemIdAtFetchTime = item.id.toString()
		var index = startIndex
		
		// Track indices for each rating type so we can update them after API fetch
		val ratingIndices = mutableMapOf<RatingType, Int>()
		
		// Show local ratings immediately (RT, stars)
		enabledRatings.forEach { ratingType ->
			when (ratingType) {
				RatingType.RATING_TOMATOES -> {
					item.criticRating?.let { rating ->
						val iconRes = if (rating >= 60f) R.drawable.ic_rt_fresh else R.drawable.ic_rt_rotten
						setItemTextWithIcon(index, "${rating.toInt()}%", iconRes)
						ratingIndices[ratingType] = index++
					}
				}
				RatingType.RATING_STARS -> {
					item.communityRating?.let { rating ->
						setItemText(index, "⭐ ${String.format("%.1f", rating)}")
						ratingIndices[ratingType] = index++
					}
				}
				else -> {
					// Reserve slot for external rating
					ratingIndices[ratingType] = index++
				}
			}
		}
		
		// Fetch external ratings from MDBList API
		if (apiKey.isNotBlank()) {
			findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
				try {
					val apiRatings = mdbListRepository.getRatings(item, apiKey)
					
					if (currentItemId == itemIdAtFetchTime && apiRatings != null) {
						withContext(Dispatchers.Main) {
							if (currentItemId == itemIdAtFetchTime) {
								enabledRatings.forEach { ratingType ->
									val ratingIndex = ratingIndices[ratingType] ?: return@forEach
									
									val source = when (ratingType) {
										RatingType.RATING_RT_AUDIENCE -> "tomatoes_audience"
										RatingType.RATING_IMDB -> "imdb"
										RatingType.RATING_TMDB -> "tmdb"
										RatingType.RATING_METACRITIC -> "metacritic"
										RatingType.RATING_TRAKT -> "trakt"
										RatingType.RATING_LETTERBOXD -> "letterboxd"
										RatingType.RATING_ROGER_EBERT -> "rogerebert"
										RatingType.RATING_MYANIMELIST -> "myanimelist"
										RatingType.RATING_ANILIST -> "anilist"
										RatingType.RATING_KINOPOISK -> "kinopoisk"
										RatingType.RATING_ALLOCINE -> "allocine"
										RatingType.RATING_DOUBAN -> "douban"
										else -> null
									} ?: return@forEach
									
									val rating = apiRatings[source] ?: return@forEach
									
									val formattedRating = formatRating(source, rating)
									val iconRes = getIconForSource(source, rating)
									
									if (iconRes != null) {
										setItemTextWithIcon(ratingIndex, formattedRating, iconRes)
									} else {
										setItemText(ratingIndex, formattedRating)
									}
								}
							}
						}
					}
				} catch (e: Exception) {
					// Silently fail
				}
			}
		}
		
		return index
	}
	
	private fun showSingleRating(
		item: BaseItemDto,
		ratingType: RatingType,
		startIndex: Int,
		enableAdditionalRatings: Boolean
	): Int {
		var index = startIndex
		
		val preferredSource = when (ratingType) {
			RatingType.RATING_TOMATOES -> "RT"
			RatingType.RATING_RT_AUDIENCE -> if (enableAdditionalRatings) "tomatoes_audience" else "RT"
			RatingType.RATING_STARS -> null
			RatingType.RATING_IMDB -> if (enableAdditionalRatings) "imdb" else null
			RatingType.RATING_TMDB -> if (enableAdditionalRatings) "tmdb" else null
			RatingType.RATING_METACRITIC -> if (enableAdditionalRatings) "metacritic" else null
			RatingType.RATING_TRAKT -> if (enableAdditionalRatings) "trakt" else null
			RatingType.RATING_LETTERBOXD -> if (enableAdditionalRatings) "letterboxd" else null
			RatingType.RATING_ROGER_EBERT -> if (enableAdditionalRatings) "rogerebert" else null
			RatingType.RATING_MYANIMELIST -> if (enableAdditionalRatings) "myanimelist" else null
			RatingType.RATING_ANILIST -> if (enableAdditionalRatings) "anilist" else null
			RatingType.RATING_KINOPOISK -> if (enableAdditionalRatings) "kinopoisk" else null
			RatingType.RATING_ALLOCINE -> if (enableAdditionalRatings) "allocine" else null
			RatingType.RATING_DOUBAN -> if (enableAdditionalRatings) "douban" else null
			RatingType.RATING_HIDDEN -> null
		}
		
		when {
			preferredSource == null -> {
				// Show community rating (stars)
				item.communityRating?.let { rating ->
					setItemText(index++, "⭐ ${String.format("%.1f", rating)}")
				}
			}
			preferredSource == "RT" -> {
				// Show Rotten Tomatoes
				item.criticRating?.let { rating ->
					val iconRes = if (rating >= 60f) R.drawable.ic_rt_fresh else R.drawable.ic_rt_rotten
					setItemTextWithIcon(index++, "${rating.toInt()}%", iconRes)
				} ?: item.communityRating?.let { rating ->
					setItemText(index++, "⭐ ${String.format("%.1f", rating)}")
				}
			}
			else -> {
				// Fetch from API
				val apiKey = userSettingPreferences[UserSettingPreferences.mdblistApiKey]
				if (apiKey.isNotBlank()) {
					val ratingIndex = index
					val itemIdAtFetchTime = item.id.toString()
					findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
						try {
							val apiRatings = mdbListRepository.getRatings(item, apiKey)
							val preferredRating = apiRatings?.get(preferredSource)
							
							if (preferredRating != null && currentItemId == itemIdAtFetchTime) {
								withContext(Dispatchers.Main) {
									if (currentItemId == itemIdAtFetchTime) {
										val formattedRating = formatRating(preferredSource, preferredRating)
										val iconRes = getIconForSource(preferredSource, preferredRating)
										
										if (iconRes != null) {
											setItemTextWithIcon(ratingIndex, formattedRating, iconRes)
										} else {
											setItemText(ratingIndex, formattedRating)
										}
									}
								}
							}
						} catch (e: Exception) {
							// Silently fail
						}
					}
					index++
				} else {
					item.communityRating?.let { rating ->
						setItemText(index++, "⭐ ${String.format("%.1f", rating)}")
					}
				}
			}
		}
		
		return index
	}
	
	private fun formatRating(source: String, rating: Float): String {
		return when (source) {
			"imdb" -> String.format("%.1f", rating)
			"tmdb" -> rating.toInt().toString()
			"metacritic" -> rating.toInt().toString()
			"trakt" -> "${rating.toInt()}%"
			"letterboxd" -> String.format("%.1f", rating)
			"rogerebert" -> String.format("%.1f", rating)
			"myanimelist" -> String.format("%.1f", rating)
			"anilist" -> rating.toInt().toString()
			"kinopoisk" -> String.format("%.1f", rating)
			"allocine" -> String.format("%.1f", rating)
			"douban" -> String.format("%.1f", rating)
			"tomatoes_audience" -> "${(rating * 100).toInt()}%"
			else -> String.format("%.1f", rating)
		}
	}
	
	private fun getIconForSource(source: String, rating: Float? = null): Int? {
		return when (source) {
			"imdb" -> R.drawable.ic_imdb
			"tmdb" -> R.drawable.ic_tmdb
			"metacritic" -> R.drawable.ic_metacritic
			"trakt" -> R.drawable.ic_trakt
			"letterboxd" -> R.drawable.ic_letterboxd
			"rogerebert" -> R.drawable.ic_roger_ebert
			"myanimelist" -> R.drawable.ic_myanimelist
			"anilist" -> R.drawable.ic_anilist
			"kinopoisk" -> R.drawable.ic_kinopoisk
			"allocine" -> R.drawable.ic_allocine
			"douban" -> R.drawable.ic_douban
			"tomatoes_audience" -> if ((rating ?: 0f) >= 0.60f) R.drawable.ic_rt_audience_fresh else R.drawable.ic_rt_audience_rotten
			else -> null
		}
	}
	
	private fun setItemText(index: Int, text: String) {
		if (index < items.size) {
			items[index].apply {
				this.text = text
				visibility = VISIBLE
			}
		}
	}
	
	private fun setItemTextWithIcon(index: Int, text: String, iconRes: Int) {
		if (index < items.size) {
			items[index].apply {
				this.text = text
				val drawable = ContextCompat.getDrawable(context, iconRes)
				drawable?.setBounds(0, 0, dpToPx(16), dpToPx(16))
				setCompoundDrawables(drawable, null, null, null)
				compoundDrawablePadding = dpToPx(4)
				visibility = VISIBLE
			}
		}
	}
	
	private fun dpToPx(dp: Int): Int {
		return (dp * context.resources.displayMetrics.density).toInt()
	}
}
