package org.jellyfin.androidtv.ui.home.mediabar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the SponsorBlock API.
 * Used to fetch sponsor/intro/outro segments for YouTube videos
 * so the media bar trailer preview can skip to the actual content.
 */
object SponsorBlockApi {

	private const val BASE_URL = "https://sponsor.ajay.app/api"

	/**
	 * Represents a segment returned by SponsorBlock
	 */
	data class Segment(
		val startTime: Double,
		val endTime: Double,
		val category: String,
		val actionType: String,
	)

	/**
	 * Fetch skip segments for a given YouTube video ID.
	 *
	 * @param videoId The YouTube video ID
	 * @param categories Categories to fetch (default: sponsor, selfpromo, intro, outro, interaction, music_offtopic)
	 * @return List of segments to skip, or empty list if none found / error
	 */
	suspend fun getSkipSegments(
		videoId: String,
		categories: List<String> = listOf("sponsor", "selfpromo", "intro", "outro", "interaction", "music_offtopic"),
	): List<Segment> = withContext(Dispatchers.IO) {
		try {
			val categoriesParam = categories.joinToString(",") { "\"$it\"" }
			val url = URL("$BASE_URL/skipSegments?videoID=$videoId&categories=[$categoriesParam]")

			val connection = url.openConnection() as HttpURLConnection
			connection.requestMethod = "GET"
			connection.connectTimeout = 5000
			connection.readTimeout = 5000
			connection.setRequestProperty("Accept", "application/json")

			try {
				val responseCode = connection.responseCode
				if (responseCode == 200) {
					val responseBody = connection.inputStream.bufferedReader().readText()
					parseSegments(responseBody)
				} else if (responseCode == 404) {
					// No segments found for this video - this is normal
					Timber.d("SponsorBlock: No segments found for video $videoId")
					emptyList()
				} else {
					Timber.w("SponsorBlock: Unexpected response code $responseCode for video $videoId")
					emptyList()
				}
			} finally {
				connection.disconnect()
			}
		} catch (e: Exception) {
			Timber.w(e, "SponsorBlock: Failed to fetch segments for video $videoId")
			emptyList()
		}
	}

	private fun parseSegments(json: String): List<Segment> {
		return try {
			val array = JSONArray(json)
			(0 until array.length()).mapNotNull { i ->
				val obj = array.getJSONObject(i)
				val segment = obj.getJSONArray("segment")
				val startTime = segment.getDouble(0)
				val endTime = segment.getDouble(1)
				val category = obj.getString("category")
				val actionType = obj.optString("actionType", "skip")
				Segment(startTime, endTime, category, actionType)
			}
		} catch (e: Exception) {
			Timber.w(e, "SponsorBlock: Failed to parse segments JSON")
			emptyList()
		}
	}

	/**
	 * Calculate the best start time for a trailer preview.
	 * Finds the first non-sponsored moment after skipping any intro/sponsor segments.
	 *
	 * @param segments The SponsorBlock segments for the video
	 * @return The recommended start time in seconds
	 */
	fun calculateStartTime(segments: List<Segment>): Double {
		if (segments.isEmpty()) return 0.0

		// Sort segments by start time
		val sorted = segments.sortedBy { it.startTime }

		// Find segments that start at or near the beginning (within 5 seconds)
		// and skip past them
		var currentTime = 0.0
		for (segment in sorted) {
			// If this segment overlaps with our current position, skip past it
			if (segment.startTime <= currentTime + 2.0) {
				currentTime = maxOf(currentTime, segment.endTime)
			} else {
				// Found a gap - this is a good start point
				break
			}
		}

		return currentTime
	}
}
