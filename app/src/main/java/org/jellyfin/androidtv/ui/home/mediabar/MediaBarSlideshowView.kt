package org.jellyfin.androidtv.ui.home.mediabar

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.MdbListRepository
import org.jellyfin.androidtv.data.repository.RatingIconProvider
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.constant.NavbarPosition
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.shared.LogoView
import org.jellyfin.androidtv.util.TimeUtils
import org.jellyfin.androidtv.util.isImagePrimarilyDark
import org.jellyfin.androidtv.util.toHtmlSpanned
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.compose.koinInject
import timber.log.Timber

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
	val isFocused by viewModel.isFocused.collectAsState()
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	val userPreferences = koinInject<UserPreferences>()

	// Check if sidebar is enabled
	val isSidebarEnabled = userPreferences[UserPreferences.navbarPosition] == NavbarPosition.LEFT

	// Get overlay preferences
	val overlayOpacity = userSettingPreferences[UserSettingPreferences.mediaBarOverlayOpacity] / 100f
	val overlayColor = when (userSettingPreferences[UserSettingPreferences.mediaBarOverlayColor]) {
		"black" -> Color.Black
		"dark_blue" -> Color(0xFF1A2332)
		"purple" -> Color(0xFF4A148C)
		"teal" -> Color(0xFF00695C)
		"navy" -> Color(0xFF0D1B2A)
		"charcoal" -> Color(0xFF36454F)
		"brown" -> Color(0xFF3E2723)
		"dark_red" -> Color(0xFF8B0000)
		"dark_green" -> Color(0xFF0B4F0F)
		"slate" -> Color(0xFF475569)
		"indigo" -> Color(0xFF1E3A8A)
		else -> Color.Gray
	}

	DisposableEffect(Unit) {
		onDispose {
			viewModel.setFocused(false)
		}
	}

	// When focus returns to Media Bar and it's empty, trigger a reload
	LaunchedEffect(isFocused) {
		if (isFocused && state is MediaBarState.Loading) {
			// Content will load automatically from state
		}
	}

	// Get root view to find sidebar
	val rootView = LocalView.current.rootView

	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(235.dp)
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
						if (isSidebarEnabled) {
							// Find sidebar and request focus
							val sidebar = rootView.findViewById<android.view.View?>(R.id.sidebar)
							if (sidebar != null && sidebar.visibility == android.view.View.VISIBLE) {
								sidebar.requestFocus()
								true
							} else {
								false
							}
						} else {
							viewModel.previousSlide()
							true
						}
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
				val item = currentState.items.getOrNull(playbackState.currentIndex)

				Crossfade(
					targetState = item?.logoUrl,
					animationSpec = tween(300),
					label = "mediabar_logo_transition",
					modifier = Modifier
						.align(Alignment.TopStart)
						.offset(x = 70.dp, y = (-220).dp)
						.width(250.dp)
						.height(100.dp)
				) { logoUrl ->
					if (logoUrl != null) {
						LogoView(
							url = logoUrl,
							modifier = Modifier.fillMaxSize()
						)
					}
				}

				// Info overlay at bottom
				Box(
					modifier = Modifier
						.align(Alignment.BottomStart)
						.fillMaxWidth()
						.padding(start = 43.dp, end = 43.dp, bottom = 30.dp),
				) {
					if (item != null) {
						MediaInfoOverlay(
							item = item,
							overlayColor = overlayColor,
							overlayOpacity = overlayOpacity,
							modifier = Modifier.fillMaxWidth()
						)
					}
				}

				// Navigation arrows
				if (currentState.items.size > 1) {
					// Left arrow (hidden when sidebar is enabled)
					if (!isSidebarEnabled) {
						Box(
							modifier = Modifier
								.align(Alignment.TopStart)
								.offset(y = (-70).dp)
								.padding(start = 5.dp)
								.size(48.dp)
								.background(overlayColor.copy(alpha = overlayOpacity), CircleShape),
							contentAlignment = Alignment.Center
						) {
							Icon(
								painter = painterResource(id = R.drawable.chevron_left),
								contentDescription = "Previous",
								tint = Color.White.copy(alpha = 0.9f),
								modifier = Modifier.size(24.dp)
							)
						}
					}

					// Right arrow
					Box(
						modifier = Modifier
							.align(Alignment.TopEnd)
							.offset(y = (-70).dp)
							.padding(end = 16.dp)
							.size(48.dp)
							.background(overlayColor.copy(alpha = overlayOpacity), CircleShape),
						contentAlignment = Alignment.Center
					) {
						Icon(
							painter = painterResource(id = R.drawable.chevron_right),
							contentDescription = "Next",
							tint = Color.White.copy(alpha = 0.9f),
							modifier = Modifier.size(24.dp)
						)
					}

					// Indicator dots - centered at bottom
					Box(
						modifier = Modifier
							.align(Alignment.BottomCenter)
							.padding(bottom = 8.dp)
					) {
						CarouselIndicatorDots(
							totalItems = currentState.items.size,
							currentIndex = playbackState.currentIndex,
							overlayColor = overlayColor,
							overlayOpacity = overlayOpacity
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
	overlayColor: Color,
	overlayOpacity: Float,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
			.background(
				brush = Brush.verticalGradient(
					colors = listOf(
						overlayColor.copy(alpha = overlayOpacity),
						overlayColor.copy(alpha = overlayOpacity)
					)
				),
				shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
			)
			.padding(16.dp)
	) {
		Column(
			modifier = Modifier.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			// Metadata + genres row
			val context = LocalContext.current
			val infoParts = buildList {
				item.year?.let { add(it.toString()) }
				item.rating?.let { add(it) }
				if (item.itemType != BaseItemKind.SERIES) {
					item.runtime?.let { add(TimeUtils.formatRuntimeHoursMinutes(context, it)) }
				}
				if (item.genres.isNotEmpty()) {
					add(item.genres.joinToString(" • "))
				}
			}
			if (infoParts.isNotEmpty()) {
				Text(
					text = infoParts.joinToString(" • "),
					fontSize = 16.sp,
					color = Color.White
				)
			}

			// Ratings row
			MediaBarRating(item = item)

			// Overview
			item.overview?.let { overview ->
				Text(
					text = overview.toHtmlSpanned().toString(),
					fontSize = 14.sp,
					color = Color.White,
					maxLines = 3,
					overflow = TextOverflow.Ellipsis,
					lineHeight = 20.sp
				)
			}
		}
	}
}

@Composable
private fun CarouselIndicatorDots(
	totalItems: Int,
	currentIndex: Int,
	overlayColor: Color,
	overlayOpacity: Float,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier
			.padding(top = 80.dp) // Push dots down much lower
			.background(
				color = overlayColor.copy(alpha = overlayOpacity * 0.6f),
				shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
			)
			.padding(horizontal = 12.dp, vertical = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		repeat(totalItems) { index ->
			Box(
				modifier = Modifier
					.size(if (index == currentIndex) 10.dp else 8.dp)
					.background(
						color = if (index == currentIndex)
							Color.White
						else
							Color.White.copy(alpha = 0.5f),
						shape = CircleShape
					)
			)
		}
	}
}

@Composable
private fun LoadingView() {
	// Show empty transparent view during loading
	// Background is handled by HomeFragment
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Transparent)
	)
}

@Composable
private fun ErrorView(message: String) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Gray.copy(alpha = 0.5f)),
		contentAlignment = Alignment.Center
	) {
		Text(
			text = message,
			fontSize = 16.sp,
			color = Color.White.copy(alpha = 0.7f)
		)
	}
}
@Composable
private fun MediaBarRating(item: MediaBarSlideItem) {
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	val mdbListRepository = koinInject<MdbListRepository>()
	val apiClient = koinInject<ApiClient>()
	val baseUrl = apiClient.baseUrl

	val enableAdditionalRatings = userSettingPreferences[UserSettingPreferences.enableAdditionalRatings]

	var apiRatings by remember { mutableStateOf<Map<String, Float>?>(null) }

	val needsExternalRating = enableAdditionalRatings && 
		(item.tmdbId != null || item.imdbId != null)

	var isLoading by remember { mutableStateOf(needsExternalRating) }

	if (needsExternalRating) {
		LaunchedEffect(item.itemId) {
			if (item.tmdbId == null && item.imdbId == null) {
				isLoading = false
				return@LaunchedEffect
			}
			
			isLoading = true
			try {
				val fakeItem = org.jellyfin.sdk.model.api.BaseItemDto(
					id = item.itemId,
					name = item.title,
					type = item.itemType,
					providerIds = buildMap {
						item.tmdbId?.let { put("Tmdb", it) }
						item.imdbId?.let { put("Imdb", it) }
					}
				)
				apiRatings = mdbListRepository.getRatings(fakeItem)
			} catch (e: Exception) {
			} finally {
				isLoading = false
			}
		}
	}

	val allRatings = remember(apiRatings, item.criticRating, item.communityRating) {
		linkedMapOf<String, Float>().apply {
			item.communityRating?.let { put("stars", it) }
			apiRatings?.forEach { (source, value) ->
				if (source == "tomatoes" && item.criticRating != null) return@forEach
				put(source, value)
			}
			// Fallback: if API didn't provide tomatoes but item has criticRating
			if ("tomatoes" !in this) {
				item.criticRating?.let { put("tomatoes", it.toFloat()) }
			}
		}
	}

	if (isLoading && needsExternalRating) {
		Box(modifier = Modifier.height(21.dp))
		return
	}

	// Show ratings in a wrapping flow row
	@OptIn(ExperimentalLayoutApi::class)
	FlowRow(
		verticalArrangement = Arrangement.spacedBy(4.dp),
		horizontalArrangement = Arrangement.spacedBy(16.dp)
	) {
		allRatings.forEach { (source, value) ->
			if (!enableAdditionalRatings && source != "stars" && source != "tomatoes") return@forEach
			SingleRating(source = source, rating = value, baseUrl = baseUrl)
		}
	}
}

@Composable
private fun SingleRating(source: String, rating: Float, baseUrl: String?) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		val displayText = when (source) {
			"tomatoes" -> "${rating.toInt()}%"
			"popcorn" -> "${rating.toInt()}%"
			"stars" -> String.format("%.1f", rating)
			"imdb", "myanimelist" -> String.format("%.1f", rating)
			"tmdb", "metacritic", "metacriticuser", "trakt", "anilist" -> "${rating.toInt()}%"
			"letterboxd", "rogerebert" -> String.format("%.1f", rating)
			else -> String.format("%.1f", rating)
		}

		if (source == "stars") {
			Text(
				text = "★",
				color = Color(0xFFFFD700),
				fontSize = 16.sp
			)
			Spacer(modifier = Modifier.width(4.dp))
		} else {
			val scorePercent = rating.toInt()
			val icon = RatingIconProvider.getIcon(baseUrl, source, scorePercent)
			icon?.let {
				when (it) {
					is RatingIconProvider.RatingIcon.ServerUrl -> coil3.compose.AsyncImage(
						model = it.url,
						contentDescription = source,
						modifier = Modifier.size(20.dp)
					)
					is RatingIconProvider.RatingIcon.LocalDrawable -> Image(
						painter = painterResource(id = it.resId),
						contentDescription = source,
						modifier = Modifier.size(20.dp)
					)
				}
				Spacer(modifier = Modifier.width(6.dp))
			}
		}

		Text(
			text = displayText,
			fontSize = 16.sp,
			color = Color.White
		)
	}
}