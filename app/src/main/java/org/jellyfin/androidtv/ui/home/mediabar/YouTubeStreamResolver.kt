package org.jellyfin.androidtv.ui.home.mediabar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream
import timber.log.Timber
import java.util.Locale

/**
 * Resolves direct video stream URLs from YouTube video IDs using
 * NewPipe Extractor, which properly handles YouTube's n-parameter
 * descrambling to avoid CDN throttling / HTTP 403 errors.
 *
 * This resolver picks:
 *  1. The best H.264 (avc1) video-only stream ≤ 720p (widest device compatibility)
 *  2. Falls back to VP9 or AV1 if no avc1 is available
 *  3. The best AAC (mp4a) audio stream for the users preferred language or
 *  4. The best AAC (mp4a) audio stream for the audio track if no language match
 */
object YouTubeStreamResolver {

	private const val TAG = "YouTubeStream"

	@Volatile
	private var initialized = false

	data class StreamInfo(
		val videoUrl: String,
		val audioUrl: String?,
		val isVideoOnly: Boolean,
	)

	@Synchronized
	private fun ensureInitialized() {
		if (!initialized) {
			NewPipe.init(NewPipeDownloader.getInstance())
			initialized = true
			Timber.d("$TAG: NewPipe Extractor initialized")
		}
	}

	suspend fun resolveStream(videoId: String, lang: String? = null): StreamInfo? = withContext(Dispatchers.IO) {
		try {
			ensureInitialized()
			val result = extractStreams(videoId, lang)
			if (result != null) {
				Timber.d("$TAG: Resolved stream for $videoId via NewPipe Extractor")
			} else {
				Timber.w("$TAG: NewPipe Extractor returned no usable streams for $videoId")
			}
			result
		} catch (e: Throwable) {
			Timber.w(e, "$TAG: NewPipe Extractor failed for $videoId")
			null
		}
	}

	private fun extractStreams(videoId: String, lang: String? = null): StreamInfo? {
		val url = "https://www.youtube.com/watch?v=$videoId"
		val extractor = ServiceList.YouTube.getStreamExtractor(url)
		extractor.fetchPage()

		val videoOnlyStreams = extractor.videoOnlyStreams
			.orEmpty()
			.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotBlank() }

		val muxedStreams = extractor.videoStreams
			.orEmpty()
			.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotBlank() }

		val audioStreams = extractor.audioStreams
			.orEmpty()
			.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotBlank() }

		val bestVideo = pickBestVideo(videoOnlyStreams)
		if (bestVideo != null) {
			val bestAudio = pickBestAudio(audioStreams, lang)
			Timber.d(
				"$TAG: Selected video-only: %s@%sp, audio: %s@%sbps, lang: %s",
				bestVideo.codec, bestVideo.height,
				bestAudio?.codec ?: "none", bestAudio?.averageBitrate ?: 0,
				bestAudio?.audioLocale?.language ?: "none",
			)
			return StreamInfo(
				videoUrl = bestVideo.content,
				audioUrl = bestAudio?.content,
				isVideoOnly = true,
			)
		}

		val bestMuxed = pickBestVideo(muxedStreams)
		if (bestMuxed != null) {
			Timber.d("$TAG: Using muxed stream %s@%sp", bestMuxed.codec, bestMuxed.height)
			return StreamInfo(
				videoUrl = bestMuxed.content,
				audioUrl = null,
				isVideoOnly = false,
			)
		}

		return null
	}

	private fun pickBestVideo(streams: List<VideoStream>): VideoStream? {
		val preferred = streams
			.filter { it.height in 1..720 }
			.sortedWith(compareBy<VideoStream> { codecPriority(it.codec) }.thenByDescending { it.height })
			.firstOrNull()
		if (preferred != null) return preferred

		return streams
			.sortedWith(compareBy<VideoStream> { codecPriority(it.codec) }.thenBy { it.height })
			.firstOrNull()
	}

	private fun pickBestAudio(streams: List<AudioStream>, lang: String? = null): AudioStream? {
		val normalized = if (lang.isNullOrBlank()) null else iso3ToIso2(lang)
		// Filter by preferred language, if provided
		val languageMatches = if (normalized != null) {
			streams.filter {
				it.audioLocale?.language?.lowercase() == normalized
			}
		} else emptyList()
		Timber.d("YoutubeStreamResolver: Found ${languageMatches.size} audio streams for language: $normalized")
		// If we found any matching streams, pick from these
		val candidates = languageMatches.ifEmpty {
			// Otherwise, fall back to all streams
			Timber.d("YoutubeStreamResolver: No matching streams found for language: $normalized -- falling back to all")
			streams
		}
		Timber.d("YoutubeStreamResolver: Picking from ${candidates.size} streams")
		// Pick the best quality from available streams
		return candidates
			.sortedWith(
				compareBy<AudioStream> {
					if (it.codec?.startsWith("mp4a") == true) 0 else 1
				}.thenByDescending { it.averageBitrate }
			)
			.firstOrNull()
	}

	private fun codecPriority(codec: String?): Int = when {
		codec == null -> 4
		codec.startsWith("avc1") -> 0
		codec.startsWith("vp09") || codec.startsWith("vp9") -> 1
		codec.startsWith("av01") -> 2
		else -> 3
	}
	// lazy map to convert ISO 3 letter codes to 2 letter
	// eng -> en, spa -> es, ...
	private val iso3ToIso2Map: Map<String, String> by lazy {
		Locale.getAvailableLocales()
			.mapNotNull { locale ->
				runCatching {
					val iso3 = locale.isO3Language.lowercase()
					val iso2 = locale.language.lowercase()
					if (iso3.isNotBlank() && iso2.isNotBlank()) {
						iso3 to iso2
					} else {
						null
					}
				}.getOrNull()
			}
			.toMap()
	}

	// convert ISO 3 letter codes to 2 letter
	// eng -> en, spa -> es, ...
	private fun iso3ToIso2(iso3: String): String? =
		iso3ToIso2Map[iso3.lowercase()]

	/*
	// pretty print helper for AudioStream
	private fun AudioStream.describe(): String {
		val lang = audioLocale?.language ?: "?"
		val country = audioLocale?.country ?: "?"
		val tag = audioLocale?.toLanguageTag() ?: "?"
		val codec = codec ?: "?"
		val bitrate = averageBitrate

		return "AudioStream(lang=$lang, country=$country, tag=$tag, codec=$codec, bitrate=${bitrate}bps)"
	}
	*/
}
