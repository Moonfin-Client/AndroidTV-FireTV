package org.jellyfin.androidtv.ui.home.mediabar

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
