package org.jellyfin.androidtv.ui.home.mediabar

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text

/**
 * Media Bar Slideshow Compose component
 * Displays a featured content slideshow with backdrop images and Ken Burns animation
 */
@Composable
fun MediaBarSlideshowView(
	viewModel: MediaBarSlideshowViewModel,
	modifier: Modifier = Modifier,
	onItemClick: (MediaBarSlideItem) -> Unit = {},
) {
	val state by viewModel.state.collectAsState()
	val playbackState by viewModel.playbackState.collectAsState()

	DisposableEffect(Unit) {
		onDispose {
			viewModel.setFocused(false)
		}
	}

	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(235.dp) // Increased 8% from 217dp
			.onFocusChanged { focusState ->
				viewModel.setFocused(focusState.hasFocus)
			}
			.focusable(enabled = true) // Make focusable so it can receive focus
			.onKeyEvent { keyEvent ->
				if (keyEvent.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) {
					return@onKeyEvent false
				}
				
				when (keyEvent.key) {
					Key.DirectionLeft, Key.MediaPrevious -> {
						viewModel.previousSlide()
						true
					}
					Key.DirectionRight, Key.MediaNext -> {
						viewModel.nextSlide()
						true
					}
					Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
						viewModel.togglePause()
						true
					}
					Key.Enter, Key.DirectionCenter -> {
						// Handle center/enter key press to navigate to item details
						val currentState = state
						if (currentState is MediaBarState.Ready) {
							val currentItem = currentState.items.getOrNull(playbackState.currentIndex)
							if (currentItem != null) {
								onItemClick(currentItem)
								true
							} else false
						} else false
					}
					// Don't consume DirectionDown/DirectionUp - let Leanback handle row navigation
					else -> false
				}
			}
	) {
		when (val currentState = state) {
			is MediaBarState.Loading -> {
				LoadingView()
			}
			is MediaBarState.Ready -> {
				// Logo and backdrop are managed by HomeFragment
				// Media info overlay (with padding)
				Box(
					modifier = Modifier
						.align(Alignment.BottomStart)
						.padding(horizontal = 43.dp, vertical = 5.dp)
				) {
					val item = currentState.items.getOrNull(playbackState.currentIndex)
					if (item != null) {
						MediaInfoOverlay(
							item = item
						)
					}
				}
				
				// Navigation arrows (without padding, close to edges, raised by 40%)
				if (currentState.items.size > 1) {
					// Left arrow - closer to left edge
					Box(
						modifier = Modifier
							.align(Alignment.TopStart)
							.padding(start = 16.dp, top = 0.dp)
							.size(48.dp)
							.background(Color.Black.copy(alpha = 0.3f), CircleShape),
						contentAlignment = Alignment.Center
					) {
						Text(
							text = "◀",
							color = Color.White.copy(alpha = 0.9f),
							fontSize = 24.sp
						)
					}
					
					// Right arrow - closer to right edge
					Box(
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(end = 16.dp, top = 0.dp)
							.size(48.dp)
							.background(Color.Black.copy(alpha = 0.3f), CircleShape),
						contentAlignment = Alignment.Center
					) {
						Text(
							text = "▶",
							color = Color.White.copy(alpha = 0.9f),
							fontSize = 24.sp
						)
					}
				}
			}
			is MediaBarState.Error -> {
				ErrorView(message = currentState.message)
			}
			is MediaBarState.Disabled -> {
				// Don't show anything
			}
		}
	}
}

@Composable
private fun MediaInfoOverlay(
	item: MediaBarSlideItem,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
			.width(600.dp)
			.background(
				brush = Brush.verticalGradient(
					colors = listOf(
						Color.Black.copy(alpha = 0.25f),
						Color.Black.copy(alpha = 0.15f)
					)
				),
				shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
			)
			.padding(16.dp)
	) {
		Column(
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			// Title (only if no logo)
			if (item.logoUrl == null) {
				Text(
					text = item.title,
					fontSize = 32.sp,
					fontWeight = FontWeight.Bold,
					color = Color.White,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis
				)
			}

		// Metadata row
		Row(
			horizontalArrangement = Arrangement.spacedBy(16.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			item.year?.let { year ->
				Text(
					text = year.toString(),
					fontSize = 16.sp,
					color = Color.White.copy(alpha = 0.9f)
				)
			}

			item.rating?.let { rating ->
				Text(
					text = rating,
					fontSize = 16.sp,
					color = Color.White.copy(alpha = 0.9f)
				)
			}

			item.runtime?.let { runtime ->
				Text(
					text = runtime,
					fontSize = 16.sp,
					color = Color.White.copy(alpha = 0.9f)
				)
			}

			// Rating indicators
			item.communityRating?.let { rating ->
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(
						text = "★",
						color = Color(0xFFFFD700),
						fontSize = 16.sp
					)
					Spacer(modifier = Modifier.width(4.dp))
					Text(
						text = String.format("%.1f", rating),
						fontSize = 16.sp,
						color = Color.White.copy(alpha = 0.9f)
					)
				}
			}
		}

		// Genres
		if (item.genres.isNotEmpty()) {
			Text(
				text = item.genres.joinToString(" • "),
				fontSize = 14.sp,
				color = Color.White.copy(alpha = 0.8f)
			)
		}

		// Overview
		item.overview?.let { overview ->
			Text(
				text = overview,
				fontSize = 14.sp,
				color = Color.White.copy(alpha = 0.85f),
				maxLines = 3,
				overflow = TextOverflow.Ellipsis,
				lineHeight = 20.sp
			)
		}
		}
	}
}

@Composable
private fun LoadingView() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black),
		contentAlignment = Alignment.Center
	) {
		Text(
			text = "Loading featured content...",
			fontSize = 16.sp,
			color = Color.White.copy(alpha = 0.7f)
		)
	}
}

@Composable
private fun ErrorView(message: String) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black.copy(alpha = 0.5f)),
		contentAlignment = Alignment.Center
	) {
		Text(
			text = message,
			fontSize = 16.sp,
			color = Color.White.copy(alpha = 0.7f)
		)
	}
}
