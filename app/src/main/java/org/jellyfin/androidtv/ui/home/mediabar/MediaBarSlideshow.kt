package org.jellyfin.androidtv.ui.home.mediabar

import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

/**
 * Configuration for the Media Bar slideshow feature
 */
data class MediaBarConfig(
	val shuffleIntervalMs: Long = 7000,
	val fadeTransitionDurationMs: Long = 500,
	val maxItems: Int,
	val enableKenBurnsAnimation: Boolean = true,
	val preloadCount: Int = 3,
)

/**
 * Represents a single slide item in the Media Bar slideshow
 */
data class MediaBarSlideItem(
	val itemId: UUID,
	val serverId: UUID?,
	val title: String,
	val overview: String?,
	val backdropUrl: String?,
	val logoUrl: String?,
	val rating: String?,
	val year: Int?,
	val genres: List<String>,
	val runtime: Long?,
	val criticRating: Int?,
	val communityRating: Float?,
	val tmdbId: String? = null,
	val imdbId: String? = null,
	val itemType: BaseItemKind = BaseItemKind.MOVIE,
)

/**
 * State of the Media Bar slideshow
 */
sealed class MediaBarState {
	object Loading : MediaBarState()
	data class Ready(val items: List<MediaBarSlideItem>) : MediaBarState()
	data class Error(val message: String) : MediaBarState()
	object Disabled : MediaBarState()
}

/**
 * Slideshow playback state
 */
data class SlideshowPlaybackState(
	val currentIndex: Int = 0,
	val isPaused: Boolean = false,
	val isTransitioning: Boolean = false,
)

/**
 * State of the trailer preview for the current slide
 */
sealed class TrailerPreviewState {
	/** No trailer is being loaded or shown */
	object Idle : TrailerPreviewState()

	/** Waiting for trailer info to be resolved from API */
	object WaitingToPlay : TrailerPreviewState()

	/** Trailer info resolved - ExoPlayer is loading/buffering in the background (invisible) */
	data class Buffering(val info: TrailerPreviewInfo) : TrailerPreviewState()

	/** Image display delay has elapsed - trailer fades in and plays */
	data class Playing(val info: TrailerPreviewInfo) : TrailerPreviewState()

	/** No YouTube trailer available for this item */
	object Unavailable : TrailerPreviewState()
}
