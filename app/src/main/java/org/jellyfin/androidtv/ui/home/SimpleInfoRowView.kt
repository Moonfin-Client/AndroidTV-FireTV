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
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.MdbListRepository
import org.jellyfin.androidtv.data.repository.RatingIconProvider
import org.jellyfin.androidtv.data.repository.TmdbRepository
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.composable.getResolutionName
import org.jellyfin.androidtv.util.TimeUtils
import org.jellyfin.sdk.api.client.ApiClient
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
	private val tmdbRepository by inject<TmdbRepository>()
	private val apiClient by inject<ApiClient>()
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
		items.forEach { 
			it.visibility = GONE
			it.text = ""
			it.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
		}
		
		if (item == null) {
			currentItemId = null
			return
		}
		
		currentItemId = item.id.toString()
		
		val metadataItems = mutableListOf<String>()
		
		val dateText = when (item.type) {
			BaseItemKind.SERIES -> item.productionYear?.toString()
			BaseItemKind.EPISODE -> item.premiereDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
			else -> item.productionYear?.toString() 
				?: item.premiereDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
		}
		dateText?.let { metadataItems.add(it) }
		
		if (item.type == BaseItemKind.EPISODE) {
			val seasonNum = item.parentIndexNumber
			val episodeNum = item.indexNumber
			if (seasonNum != null && episodeNum != null) {
				metadataItems.add("S${seasonNum}E${episodeNum}")
			}
		}
		
		item.officialRating?.let { rating ->
			if (rating.isNotBlank()) {
				metadataItems.add(rating)
			}
		}
		
		if (item.type == BaseItemKind.MOVIE) {
			item.runTimeTicks?.let { ticks ->
				val runtimeMs = ticks / 10_000
				metadataItems.add(TimeUtils.formatRuntimeHoursMinutes(context, runtimeMs))
			}
		}
		
		val mediaSource = item.mediaSources?.firstOrNull()
		val videoStream = mediaSource?.mediaStreams?.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO }
		
		val hasSdhSubtitles = mediaSource?.mediaStreams?.any { 
			it.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE && it.isHearingImpaired 
		} == true
		val hasCcSubtitles = mediaSource?.mediaStreams?.any { 
			it.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE && !it.isHearingImpaired 
		} == true
		
		if (hasSdhSubtitles) metadataItems.add("SDH")
		if (hasCcSubtitles) metadataItems.add("CC")
		
		if (videoStream?.width != null && videoStream.height != null) {
			val resolution = getResolutionName(
				context = context,
				width = videoStream.width!!,
				height = videoStream.height!!,
				interlaced = videoStream.isInterlaced
			)
			metadataItems.add(resolution)
		}
		
		var index = 0
		if (metadataItems.isNotEmpty()) {
			setItemText(index++, metadataItems.joinToString(" • "))
		}
		
		val itemIdAtFetchTime = item.id.toString()
		val isEpisode = item.type == BaseItemKind.EPISODE
		val enableAdditionalRatings = userSettingPreferences[UserSettingPreferences.enableAdditionalRatings]
		val enableEpisodeRatings = userSettingPreferences[UserSettingPreferences.enableEpisodeRatings]

		val starsRating = item.communityRating
		if (starsRating != null) {
			setItemText(index++, "⭐ ${String.format("%.1f", starsRating)}")
		} else if (isEpisode) {
			val starsIndex = index++
			findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
				try {
					val seriesRating = tmdbRepository.getSeriesCommunityRating(item)
					if (currentItemId == itemIdAtFetchTime && seriesRating != null) {
						withContext(Dispatchers.Main) {
							if (currentItemId == itemIdAtFetchTime) {
								setItemText(starsIndex, "⭐ ${String.format("%.1f", seriesRating)}")
							}
						}
					}
				} catch (_: Exception) { }
			}
		}

		item.criticRating?.let { critic ->
			val scorePercent = critic.toInt()
			val formattedRating = "${scorePercent}%"
			val icon = RatingIconProvider.getIcon(apiClient.baseUrl, "tomatoes", scorePercent)
			if (icon != null) {
				setItemTextWithIcon(index++, formattedRating, icon)
			} else {
				setItemText(index++, formattedRating)
			}
		}

		if (enableEpisodeRatings && isEpisode) {
			val episodeRatingIndex = index++
			findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
				try {
					val episodeRating = tmdbRepository.getEpisodeRating(item)
					if (currentItemId == itemIdAtFetchTime && episodeRating != null) {
						withContext(Dispatchers.Main) {
							if (currentItemId == itemIdAtFetchTime) {
								val formattedRating = "${(episodeRating * 10f).toInt()}%"
								val icon = RatingIconProvider.getIcon(apiClient.baseUrl, "tmdb")
								if (icon != null) setItemTextWithIcon(episodeRatingIndex, formattedRating, icon)
							}
						}
					}
				} catch (_: Exception) { }
			}
		}

		if (enableAdditionalRatings) {
			val mdbStartIndex = index
			findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
				try {
					val apiRatings = mdbListRepository.getRatings(item)
					if (currentItemId == itemIdAtFetchTime && apiRatings != null) {
						withContext(Dispatchers.Main) {
							if (currentItemId == itemIdAtFetchTime) {
								var mdbIndex = mdbStartIndex
								apiRatings.forEach { (source, value) ->
									if (source == "tomatoes" && item.criticRating != null) return@forEach
									if (isEpisode && enableEpisodeRatings && source == "tmdb") return@forEach

									val displayValue = value

									val formattedRating = formatRating(source, displayValue)
									val icon = RatingIconProvider.getIcon(apiClient.baseUrl, source, displayValue.toInt())
									if (icon != null) {
										setItemTextWithIcon(mdbIndex, formattedRating, icon)
									} else {
										setItemText(mdbIndex, formattedRating)
									}
									mdbIndex++
								}
							}
						}
					}
				} catch (_: Exception) { }
			}
		}
	}
	
	private fun formatRating(source: String, rating: Float): String {
		return when (source) {
			"imdb" -> String.format("%.1f", rating)
			"tmdb" -> "${rating.toInt()}%"
			"metacritic" -> "${rating.toInt()}%"
			"metacriticuser" -> "${rating.toInt()}%"
			"trakt" -> "${rating.toInt()}%"
			"letterboxd" -> String.format("%.1f", rating)
			"rogerebert" -> String.format("%.1f", rating)
			"myanimelist" -> String.format("%.1f", rating)
			"anilist" -> "${rating.toInt()}%"
			"tomatoes" -> "${rating.toInt()}%"
			"popcorn" -> "${rating.toInt()}%"
			else -> String.format("%.1f", rating)
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
	
	private fun setItemTextWithIcon(index: Int, text: String, icon: RatingIconProvider.RatingIcon) {
		if (index < items.size) {
			val textView = items[index]
			textView.text = text
			textView.visibility = VISIBLE

			when (icon) {
				is RatingIconProvider.RatingIcon.LocalDrawable -> {
					val drawable = ContextCompat.getDrawable(context, icon.resId)
					drawable?.setBounds(0, 0, dpToPx(16), dpToPx(16))
					textView.setCompoundDrawables(drawable, null, null, null)
					textView.compoundDrawablePadding = dpToPx(4)
				}
				is RatingIconProvider.RatingIcon.ServerUrl -> {
					val size = dpToPx(16)
					val imageLoader = SingletonImageLoader.get(context)
					val request = ImageRequest.Builder(context)
						.data(icon.url)
						.target(
							onSuccess = { image ->
								val drawable = image.asDrawable(context.resources)
								drawable.setBounds(0, 0, size, size)
								textView.setCompoundDrawables(drawable, null, null, null)
								textView.compoundDrawablePadding = dpToPx(4)
							}
						)
						.build()
					imageLoader.enqueue(request)
				}
			}
		}
	}
	
	private fun dpToPx(dp: Int): Int {
		return (dp * context.resources.displayMetrics.density).toInt()
	}
}
