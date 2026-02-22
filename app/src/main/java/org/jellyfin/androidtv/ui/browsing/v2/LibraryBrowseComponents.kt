package org.jellyfin.androidtv.ui.browsing.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.WatchedIndicatorBehavior
import org.jellyfin.androidtv.ui.base.Badge
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Seekbar
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.focusBorderColor
import org.jellyfin.androidtv.ui.browsing.composable.inforow.InfoRowCompactRatings
import org.jellyfin.androidtv.util.TimeUtils
import org.jellyfin.design.Tokens
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.compose.koinInject

// Jellyfin accent blue
val JellyfinBlue = Color(0xFF00A4DC)

// Dark navy backdrop matching the webOS/web client
val NavyBackground = Color(0xFF101528)

/**
 * Maps item kind to a short display label for the type badge.
 */
private fun getTypeBadgeLabel(kind: BaseItemKind?): String? = when (kind) {
	BaseItemKind.MOVIE -> "MOVIE"
	BaseItemKind.SERIES -> "SERIES"
	BaseItemKind.EPISODE -> "EPISODE"
	BaseItemKind.MUSIC_ALBUM -> "ALBUM"
	BaseItemKind.MUSIC_ARTIST -> "ARTIST"
	BaseItemKind.AUDIO -> "SONG"
	BaseItemKind.BOOK -> "BOOK"
	BaseItemKind.BOX_SET -> "COLLECTION"
	BaseItemKind.PERSON -> "PERSON"
	else -> null
}

/**
 * Returns the badge background color for a given item kind.
 */
private fun getTypeBadgeColor(kind: BaseItemKind?): Color = when (kind) {
	BaseItemKind.SERIES -> Color(0xFF9333EA) // Purple
	else -> JellyfinBlue
}

/**
 * Builds a compact metadata string: "2012  R  1h 30m  ★ 6.9"
 */
fun buildMetadataString(item: BaseItemDto, context: android.content.Context? = null): String {
	val parts = mutableListOf<String>()
	item.productionYear?.let { parts.add(it.toString()) }
	item.officialRating?.let { if (it.isNotBlank()) parts.add(it) }
	if (item.type == BaseItemKind.MOVIE) {
		item.runTimeTicks?.let { ticks ->
			val runtimeMs = ticks / 10_000
			if (context != null) {
				parts.add(TimeUtils.formatRuntimeHoursMinutes(context, runtimeMs))
			} else {
				val totalMinutes = (runtimeMs / 60_000).toInt()
				val hours = totalMinutes / 60
				val minutes = totalMinutes % 60
				if (hours > 0) parts.add("${hours}h ${minutes}m")
				else parts.add("${minutes}m")
			}
		}
	}
	item.communityRating?.let { parts.add("★ ${String.format("%.1f", it)}") }
	return parts.joinToString("  ")
}

/**
 * A poster card for the library grid, matching the Jellyfin web/webOS style.
 * Shows: type badge overlay, poster image, title, year / officialRating / ★ communityRating.
 * @param showLabels Whether to show the title and metadata below the poster image.
 */
@Composable
fun LibraryPosterCard(
	item: BaseItemDto,
	imageUrl: String?,
	cardWidth: Int,
	cardHeight: Int,
	onClick: () -> Unit,
	onFocused: () -> Unit,
	showLabels: Boolean = true,
	showBadge: Boolean = false,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	LaunchedEffect(isFocused) {
		if (isFocused) onFocused()
	}

	val scale = if (isFocused) 1.08f else 1.0f
	val borderColor = focusBorderColor()

	Column(
		modifier = modifier
			.width(cardWidth.dp)
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			),
		horizontalAlignment = Alignment.Start,
	) {
		// Poster image with type badge overlay
		Box(
			modifier = Modifier
				.size(width = cardWidth.dp, height = cardHeight.dp)
				.clip(RoundedCornerShape(4.dp))
				.then(
					if (isFocused) Modifier.border(2.dp, borderColor, RoundedCornerShape(4.dp))
					else Modifier
				)
				.background(Color.White.copy(alpha = 0.06f)),
		) {
			if (imageUrl != null) {
				AsyncImage(
					model = imageUrl,
					contentDescription = item.name,
					modifier = Modifier.fillMaxSize(),
					contentScale = ContentScale.Crop,
				)
			}

			// Type badge — top-left corner
			if (showBadge) {
				val badgeLabel = getTypeBadgeLabel(item.type)
				if (badgeLabel != null) {
					Box(
						modifier = Modifier
							.align(Alignment.TopStart)
							.padding(5.dp)
							.background(getTypeBadgeColor(item.type), RoundedCornerShape(3.dp))
							.padding(horizontal = 5.dp, vertical = 2.dp),
					) {
						Text(
							text = badgeLabel,
							fontSize = 8.sp,
							fontWeight = FontWeight.Bold,
							color = Color.White,
						)
					}
				}
			}

			if (item.userData?.isFavorite == true) {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.ic_heart),
					contentDescription = null,
					tint = Tokens.Color.colorRed500,
					modifier = Modifier
						.align(Alignment.TopStart)
						.padding(4.dp)
						.size(20.dp),
				)
			}

			PosterWatchIndicator(
				item = item,
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(4.dp),
			)

			val playedPercentage = item.userData?.playedPercentage
				?.toFloat()?.div(100f)
				?.coerceIn(0f, 1f)
				?.takeIf { it > 0f && it < 1f }
			if (playedPercentage != null) {
				Box(
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.fillMaxWidth()
						.padding(Tokens.Space.spaceXs),
				) {
					Seekbar(
						progress = playedPercentage,
						enabled = false,
						modifier = Modifier
							.fillMaxWidth()
							.height(4.dp),
					)
				}
			}
		}

		Spacer(modifier = Modifier.height(5.dp))

		if (showLabels) {
			// Title
			Text(
				text = item.name ?: "",
				fontSize = 13.sp,
				fontWeight = FontWeight.Medium,
				color = Color.White,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)

			// Metadata: year, officialRating, runtime, ★ communityRating
			val context = androidx.compose.ui.platform.LocalContext.current
			val meta = buildMetadataString(item, context)
			if (meta.isNotEmpty()) {
				Text(
					text = meta,
					fontSize = 11.sp,
					fontWeight = FontWeight.Normal,
					color = Color.White.copy(alpha = 0.5f),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

/**
 * A compact icon button for the library toolbar.
 * Turns solid white with a black icon when focused.
 */
@Composable
fun LibraryToolbarButton(
	iconRes: Int,
	contentDescription: String,
	isActive: Boolean = false,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	val bgColor = when {
		isFocused -> Color.White
		isActive -> Color.White.copy(alpha = 0.15f)
		else -> Color.Transparent
	}

	val tintColor = when {
		isFocused -> Color.Black
		isActive -> JellyfinBlue
		else -> Color.White.copy(alpha = 0.5f)
	}

	Box(
		modifier = modifier
			.size(34.dp)
			.clip(RoundedCornerShape(6.dp))
			.background(bgColor)
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			)
			.padding(5.dp),
		contentAlignment = Alignment.Center,
	) {
		Icon(
			imageVector = ImageVector.vectorResource(iconRes),
			contentDescription = contentDescription,
			modifier = Modifier.size(22.dp),
			tint = tintColor,
		)
	}
}

/**
 * Inline A-Z letter picker.
 */
@Composable
fun AlphaPickerBar(
	selectedLetter: String?,
	onLetterSelected: (String?) -> Unit,
	modifier: Modifier = Modifier,
) {
	val letters = listOf("#") + ('A'..'Z').map { it.toString() }

	LazyRow(
		modifier = modifier,
		horizontalArrangement = Arrangement.spacedBy(0.dp),
		contentPadding = PaddingValues(horizontal = 2.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		items(letters) { letter ->
			val isSelected = when {
				letter == "#" && selectedLetter == null -> true
				letter == selectedLetter -> true
				else -> false
			}

			AlphaPickerLetter(
				letter = letter,
				isSelected = isSelected,
				onClick = {
					if (letter == "#") onLetterSelected(null)
					else onLetterSelected(letter)
				},
			)
		}
	}
}

@Composable
private fun AlphaPickerLetter(
	letter: String,
	isSelected: Boolean,
	onClick: () -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	val textColor = when {
		isSelected -> JellyfinBlue
		isFocused -> Color.White
		else -> Color.White.copy(alpha = 0.4f)
	}

	Box(
		modifier = Modifier
			.size(width = 26.dp, height = 28.dp)
			.then(
				if (isFocused) Modifier
					.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
				else Modifier
			)
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = letter,
			fontSize = 13.sp,
			fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
			color = textColor,
		)
	}
}

/**
 * Focused item info HUD — shows title and metadata for the currently focused poster.
 * Title auto-scrolls (marquee) if too long.
 */
@Composable
fun FocusedItemHud(
	item: BaseItemDto?,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier.defaultMinSize(minHeight = 48.dp),
		verticalArrangement = Arrangement.Center,
	) {
		if (item != null) {
			Box(
				modifier = Modifier
					.basicMarquee(
						iterations = Int.MAX_VALUE,
						initialDelayMillis = 1200,
					),
			) {
				Text(
					text = item.name ?: "",
					fontSize = 20.sp,
					fontWeight = FontWeight.SemiBold,
					color = Color.White,
					maxLines = 1,
					overflow = TextOverflow.Clip,
				)
			}

			// Metadata + compact ratings on the same row
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				val context = androidx.compose.ui.platform.LocalContext.current
				val metaLine = buildMetadataString(item, context)
				if (metaLine.isNotEmpty()) {
					Text(
						text = metaLine,
						fontSize = 14.sp,
						fontWeight = FontWeight.Normal,
						color = Color.White.copy(alpha = 0.6f),
						maxLines = 1,
					)
				}

				InfoRowCompactRatings(item = item)
			}
		}
	}
}

/**
 * Library info bar showing filter/sort status and item counter.
 */
@Composable
fun LibraryStatusBar(
	statusText: String,
	counterText: String,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 60.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = statusText,
			fontSize = 11.sp,
			color = Color.White.copy(alpha = 0.3f),
		)
		Text(
			text = counterText,
			fontSize = 13.sp,
			color = Color.White.copy(alpha = 0.45f),
		)
	}
}

/**
 * Glass-morphism filter/sort dialog matching TrackSelectorDialog style.
 * Shows sort options as radio-selectable rows, plus toggle rows for favorites / unwatched.
 */
@Composable
fun FilterSortDialog(
	title: String,
	sortOptions: List<SortOption>,
	currentSort: SortOption,
	filterFavorites: Boolean,
	filterUnwatched: Boolean,
	showUnwatchedToggle: Boolean,
	onSortSelected: (SortOption) -> Unit,
	onToggleFavorites: () -> Unit,
	onToggleUnwatched: () -> Unit,
	onDismiss: () -> Unit,
) {
	val initialFocusRequester = remember { FocusRequester() }

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Box(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center,
		) {
			Column(
				modifier = Modifier
					.widthIn(min = 340.dp, max = 440.dp)
					.clip(RoundedCornerShape(20.dp))
					.background(Color(0xE6141414))
					.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
					.padding(vertical = 20.dp),
			) {
				// Title
				Text(
					text = title,
					fontSize = 20.sp,
					fontWeight = FontWeight.W600,
					color = Color.White,
					modifier = Modifier
						.padding(horizontal = 24.dp)
						.padding(bottom = 12.dp),
				)

				// Divider
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(1.dp)
						.background(Color.White.copy(alpha = 0.08f)),
				)

				Spacer(modifier = Modifier.height(4.dp))

				// Section: Sort
				Text(
					text = "Sort By",
					fontSize = 13.sp,
					fontWeight = FontWeight.W500,
					color = Color.White.copy(alpha = 0.45f),
					modifier = Modifier
						.padding(horizontal = 24.dp, vertical = 8.dp),
				)

				LazyColumn {
					itemsIndexed(sortOptions) { index, option ->
						val interactionSource = remember { MutableInteractionSource() }
						val isFocused by interactionSource.collectIsFocusedAsState()
						val isSelected = option.sortBy == currentSort.sortBy

						val focusModifier = if (index == sortOptions.indexOfFirst { it.sortBy == currentSort.sortBy }
								.coerceIn(0, sortOptions.lastIndex)
						) {
							Modifier.focusRequester(initialFocusRequester)
						} else {
							Modifier
						}

						Row(
							modifier = focusModifier
								.fillMaxWidth()
								.clickable(
									interactionSource = interactionSource,
									indication = null,
								) { onSortSelected(option) }
								.focusable(interactionSource = interactionSource)
								.background(
									if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
								)
								.padding(horizontal = 24.dp, vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							// Radio circle
							Box(
								modifier = Modifier
									.size(18.dp)
									.border(
										width = 2.dp,
										color = if (isSelected) JellyfinBlue else Color.White.copy(alpha = 0.3f),
										shape = CircleShape,
									),
								contentAlignment = Alignment.Center,
							) {
								if (isSelected) {
									Box(
										modifier = Modifier
											.size(10.dp)
											.background(JellyfinBlue, CircleShape),
									)
								}
							}

							Spacer(modifier = Modifier.width(16.dp))

							Text(
								text = option.name,
								fontSize = 16.sp,
								fontWeight = if (isSelected) FontWeight.W600 else FontWeight.W400,
								color = when {
									isSelected -> JellyfinBlue
									isFocused -> Color.White
									else -> Color.White.copy(alpha = 0.8f)
								},
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								modifier = Modifier.weight(1f),
							)
						}
					}
				}

				// Divider
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(1.dp)
						.padding(horizontal = 24.dp)
						.background(Color.White.copy(alpha = 0.06f)),
				)

				Spacer(modifier = Modifier.height(4.dp))

				// Section: Filters
				Text(
					text = "Filters",
					fontSize = 13.sp,
					fontWeight = FontWeight.W500,
					color = Color.White.copy(alpha = 0.45f),
					modifier = Modifier
						.padding(horizontal = 24.dp, vertical = 8.dp),
				)

				// Favorites toggle
				FilterToggleRow(
					label = "Favorites Only",
					isActive = filterFavorites,
					onClick = onToggleFavorites,
				)

				// Unwatched toggle
				if (showUnwatchedToggle) {
					FilterToggleRow(
						label = "Unwatched Only",
						isActive = filterUnwatched,
						onClick = onToggleUnwatched,
					)
				}
			}
		}

		LaunchedEffect(Unit) {
			initialFocusRequester.requestFocus()
		}
	}
}

/**
 * A toggle row inside the filter dialog — checkbox-like (filled/empty circle).
 */
@Composable
private fun FilterToggleRow(
	label: String,
	isActive: Boolean,
	onClick: () -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(
				interactionSource = interactionSource,
				indication = null,
			) { onClick() }
			.focusable(interactionSource = interactionSource)
			.background(
				if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
			)
			.padding(horizontal = 24.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		// Checkbox-like circle
		Box(
			modifier = Modifier
				.size(18.dp)
				.then(
					if (isActive) Modifier
						.background(JellyfinBlue, RoundedCornerShape(4.dp))
					else Modifier
						.border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
				),
			contentAlignment = Alignment.Center,
		) {
			if (isActive) {
				Text(
					text = "✓",
					fontSize = 12.sp,
					fontWeight = FontWeight.Bold,
					color = Color.White,
				)
			}
		}

		Spacer(modifier = Modifier.width(16.dp))

		Text(
			text = label,
			fontSize = 16.sp,
			fontWeight = if (isActive) FontWeight.W600 else FontWeight.W400,
			color = when {
				isActive -> JellyfinBlue
				isFocused -> Color.White
				else -> Color.White.copy(alpha = 0.8f)
			},
		)
	}
}

/**
 * Watched/unplayed indicator for library poster cards
 */
@Composable
private fun PosterWatchIndicator(
	item: BaseItemDto,
	modifier: Modifier = Modifier,
) {
	val userPreferences = koinInject<UserPreferences>()
	val watchedIndicatorBehavior = userPreferences[UserPreferences.watchedIndicatorBehavior]

	if (watchedIndicatorBehavior == WatchedIndicatorBehavior.NEVER) return
	if (watchedIndicatorBehavior == WatchedIndicatorBehavior.EPISODES_ONLY && item.type != BaseItemKind.EPISODE) return

	val isPlayed = item.userData?.played == true
	val unplayedItems = item.userData?.unplayedItemCount?.takeIf { it > 0 }

	if (isPlayed) {
		Badge(
			modifier = modifier.size(22.dp),
		) {
			Icon(
				imageVector = ImageVector.vectorResource(R.drawable.ic_watch),
				contentDescription = null,
				modifier = Modifier.size(12.dp),
			)
		}
	} else if (unplayedItems != null) {
		if (watchedIndicatorBehavior == WatchedIndicatorBehavior.HIDE_UNWATCHED) return

		Badge(
			modifier = modifier.sizeIn(minWidth = 22.dp, minHeight = 22.dp),
		) {
			Text(
				text = unplayedItems.toString(),
			)
		}
	}
}
