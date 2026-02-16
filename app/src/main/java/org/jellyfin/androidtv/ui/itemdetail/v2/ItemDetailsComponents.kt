package org.jellyfin.androidtv.ui.itemdetail.v2

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.base.button.IconButtonDefaults
import org.jellyfin.androidtv.ui.base.focusBorderColor

@Composable
fun DetailActionButton(
	label: String,
	icon: ImageVector,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	detail: String? = null,
	isActive: Boolean = false,
	activeColor: Color = Color.Unspecified,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()
	val focusColor = focusBorderColor()
	val resolvedActiveColor = if (activeColor == Color.Unspecified) focusColor else activeColor
	val focusContentColor = if (focusColor.luminance() > 0.4f) Color(0xFF0A0A0A) else Color.White

	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = modifier.width(80.dp),
	) {
		IconButton(
			onClick = onClick,
			shape = RoundedCornerShape(14.dp),
			colors = IconButtonDefaults.colors(
				containerColor = Color.White.copy(alpha = 0.08f),
				contentColor = if (isActive) resolvedActiveColor else Color.White,
				focusedContainerColor = focusColor.copy(alpha = 0.95f),
				focusedContentColor = if (isActive) resolvedActiveColor else focusContentColor,
			),
			contentPadding = PaddingValues(16.dp),
			interactionSource = interactionSource,
			modifier = Modifier.border(
				1.dp,
				Color.White.copy(alpha = 0.15f),
				RoundedCornerShape(14.dp),
			),
		) {
			Icon(
				imageVector = icon,
				contentDescription = label,
				modifier = Modifier.size(22.dp),
			)
		}

		Spacer(modifier = Modifier.height(6.dp))

		Text(
			text = label,
			fontSize = 12.sp,
			fontWeight = FontWeight.W600,
			color = Color.White.copy(alpha = 0.8f),
			textAlign = TextAlign.Center,
			maxLines = 1,
		)

		if (detail != null) {
			Text(
				text = detail,
				fontSize = 11.sp,
				color = if (isFocused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.5f),
				textAlign = TextAlign.Center,
				maxLines = if (isFocused) 2 else 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
fun MediaBadgeChip(
	badge: MediaBadge,
	modifier: Modifier = Modifier,
) {
	Text(
		text = badge.label,
		modifier = modifier
			.background(
				Color(0xB3FFFFFF),
				RoundedCornerShape(4.dp),
			)
			.padding(horizontal = 6.dp, vertical = 2.dp),
		fontSize = 11.sp,
		fontWeight = FontWeight.W700,
		color = Color.Black,
		letterSpacing = 0.5.sp,
	)
}

@Composable
fun InfoItemText(
	text: String,
	modifier: Modifier = Modifier,
) {
	Text(
		text = text,
		modifier = modifier,
		fontSize = 15.sp,
		fontWeight = FontWeight.W500,
		color = Color.White.copy(alpha = 0.7f),
	)
}

@Composable
fun InfoItemSeparator() {
	Text(
		text = "\u2022",
		modifier = Modifier.padding(horizontal = 8.dp),
		fontSize = 10.sp,
		color = Color.White.copy(alpha = 0.35f),
	)
}

@Composable
fun MetadataGroup(
	items: List<Pair<String, String>>,
	modifier: Modifier = Modifier,
) {
	if (items.isEmpty()) return

	Row(
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(Color.White.copy(alpha = 0.03f))
			.border(
				1.dp,
				Color.White.copy(alpha = 0.06f),
				RoundedCornerShape(8.dp),
			)
			.padding(vertical = 12.dp),
		verticalAlignment = Alignment.Top,
	) {
		items.forEachIndexed { index, (label, value) ->
			Column(
				modifier = Modifier
					.weight(1f)
					.padding(horizontal = 18.dp),
			) {
				Text(
					text = label.uppercase(),
					fontSize = 11.sp,
					fontWeight = FontWeight.W600,
					color = Color.White.copy(alpha = 0.4f),
					letterSpacing = 0.5.sp,
				)
				Spacer(modifier = Modifier.height(4.dp))
				Text(
					text = value,
					fontSize = 14.sp,
					color = Color.White.copy(alpha = 0.85f),
				)
			}
			if (index < items.lastIndex) {
				Box(
					Modifier
						.width(1.dp)
						.height(36.dp)
						.background(Color.White.copy(alpha = 0.08f))
				)
			}
		}
	}
}

@Composable
fun CastCard(
	name: String,
	role: String?,
	imageUrl: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = modifier
			.width(110.dp)
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			),
	) {
		Box(
			modifier = Modifier
				.size(90.dp)
				.clip(CircleShape)
				.then(
					if (isFocused) Modifier.border(2.dp, focusBorderColor(), CircleShape)
					else Modifier.border(2.dp, Color.Transparent, CircleShape)
				)
				.background(Color.White.copy(alpha = 0.05f)),
			contentAlignment = Alignment.Center,
		) {
			if (imageUrl != null) {
				AsyncImage(
					model = imageUrl,
					contentDescription = name,
					modifier = Modifier.fillMaxSize(),
					contentScale = ContentScale.Crop,
				)
			} else {
				Text(
					text = name.firstOrNull()?.toString() ?: "",
					fontSize = 32.sp,
					fontWeight = FontWeight.W600,
					color = Color.White.copy(alpha = 0.3f),
				)
			}
		}

		Spacer(modifier = Modifier.height(6.dp))

		Text(
			text = name,
			fontSize = 13.sp,
			fontWeight = FontWeight.W500,
			color = Color.White,
			textAlign = TextAlign.Center,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)

		if (role != null) {
			Text(
				text = role,
				fontSize = 11.sp,
				color = Color.White.copy(alpha = 0.5f),
				textAlign = TextAlign.Center,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
fun SeasonCard(
	name: String,
	imageUrl: String?,
	isWatched: Boolean,
	unplayedCount: Int?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = modifier.clickable(
			interactionSource = interactionSource,
			indication = null,
			onClick = onClick,
		),
	) {
		Box(
			modifier = Modifier
				.width(170.dp)
				.height(255.dp)
				.clip(RoundedCornerShape(6.dp))
				.then(
					if (isFocused) Modifier.border(2.dp, focusBorderColor(), RoundedCornerShape(6.dp))
					else Modifier.border(2.dp, Color.Transparent, RoundedCornerShape(6.dp))
				)
				.background(Color.White.copy(alpha = 0.05f)),
		) {
			if (imageUrl != null) {
				AsyncImage(
					model = imageUrl,
					contentDescription = name,
					modifier = Modifier.fillMaxSize(),
					contentScale = ContentScale.Crop,
				)
			} else {
				Box(
					modifier = Modifier.fillMaxSize(),
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = name,
						fontSize = 17.sp,
						color = Color.White.copy(alpha = 0.7f),
						textAlign = TextAlign.Center,
						modifier = Modifier.padding(12.dp),
					)
				}
			}

			if (isWatched) {
				Box(
					modifier = Modifier
						.align(Alignment.TopEnd)
						.padding(8.dp)
						.size(22.dp)
						.background(Color(0xE600A4DC), CircleShape),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_check),
						contentDescription = null,
						modifier = Modifier.size(12.dp),
						tint = Color.White,
					)
				}
			}

			if (!isWatched && unplayedCount != null && unplayedCount > 0) {
				Box(
					modifier = Modifier
						.align(Alignment.TopEnd)
						.padding(6.dp)
						.background(Color(0xFF00A4DC), RoundedCornerShape(10.dp))
						.padding(horizontal = 5.dp),
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = unplayedCount.toString(),
						fontSize = 12.sp,
						fontWeight = FontWeight.W700,
						color = Color.White,
					)
				}
			}
		}

		Spacer(modifier = Modifier.height(6.dp))

		Text(
			text = name,
			fontSize = 14.sp,
			color = Color.White.copy(alpha = 0.9f),
			textAlign = TextAlign.Center,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.width(140.dp),
		)
	}
}

@Composable
fun EpisodeCard(
	episodeNumber: Int?,
	title: String,
	runtime: String?,
	imageUrl: String?,
	progress: Double,
	isCurrent: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()
	val borderColor = focusBorderColor()

	Column(
		modifier = modifier
			.width(220.dp)
			.clip(RoundedCornerShape(6.dp))
			.then(
				if (isCurrent) Modifier.border(
					2.dp,
					borderColor.copy(alpha = 0.4f),
					RoundedCornerShape(8.dp),
				)
				else if (isFocused) Modifier.border(
					2.dp,
					borderColor,
					RoundedCornerShape(8.dp),
				)
				else Modifier.border(2.dp, Color.Transparent, RoundedCornerShape(8.dp))
			)
			.then(
				if (isCurrent) Modifier.background(borderColor.copy(alpha = 0.08f))
				else Modifier
			)
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(124.dp)
				.background(Color(0xFF111111)),
		) {
			if (imageUrl != null) {
				AsyncImage(
					model = imageUrl,
					contentDescription = title,
					modifier = Modifier.fillMaxSize(),
					contentScale = ContentScale.Crop,
				)
			}

			if (progress > 0) {
				Box(
					modifier = Modifier
						.align(Alignment.BottomStart)
						.fillMaxWidth()
						.height(2.dp)
						.background(Color.Black.copy(alpha = 0.5f)),
				) {
					Box(
						modifier = Modifier
							.fillMaxWidth(fraction = (progress / 100.0).toFloat().coerceIn(0f, 1f))
							.height(2.dp)
							.background(Color(0xFF00A4DC)),
					)
				}
			}
		}

		Row(
			modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				text = "E${episodeNumber ?: "?"}",
				fontSize = 12.sp,
				fontWeight = FontWeight.W700,
				color = Color.White.copy(alpha = 0.5f),
			)
			Spacer(modifier = Modifier.width(6.dp))
			Text(
				text = title,
				fontSize = 13.sp,
				color = Color.White.copy(alpha = 0.9f),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f),
			)
			if (runtime != null) {
				Spacer(modifier = Modifier.width(6.dp))
				Text(
					text = runtime,
					fontSize = 11.sp,
					color = Color.White.copy(alpha = 0.4f),
				)
			}
		}
	}
}

@Composable
fun SeasonEpisodeItem(
	episodeNumber: Int?,
	title: String,
	overview: String?,
	runtime: String?,
	imageUrl: String?,
	progress: Double,
	isPlayed: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()
	val seasonBorderColor = focusBorderColor()

	Row(
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(
				if (isFocused) Color.White.copy(alpha = 0.08f)
				else Color.White.copy(alpha = 0.04f)
			)
			.then(
				if (isFocused) Modifier.border(
					2.dp,
					seasonBorderColor.copy(alpha = 0.4f),
					RoundedCornerShape(8.dp),
				)
				else Modifier.border(2.dp, Color.Transparent, RoundedCornerShape(8.dp))
			)
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			),
	) {
		Box(
			modifier = Modifier
				.width(240.dp)
				.height(135.dp)
				.background(Color(0xFF111111)),
		) {
			if (imageUrl != null) {
				AsyncImage(
					model = imageUrl,
					contentDescription = title,
					modifier = Modifier.fillMaxSize(),
					contentScale = ContentScale.Crop,
				)
			}

			if (progress > 0) {
				Box(
					modifier = Modifier
						.align(Alignment.BottomStart)
						.fillMaxWidth()
						.height(3.dp)
						.background(Color.Black.copy(alpha = 0.5f)),
				) {
					Box(
						modifier = Modifier
							.fillMaxWidth(fraction = (progress / 100.0).toFloat().coerceIn(0f, 1f))
							.height(3.dp)
							.background(Color(0xFF00A4DC)),
					)
				}
			}

			if (isPlayed) {
				Box(
					modifier = Modifier
						.align(Alignment.TopEnd)
						.padding(6.dp)
						.size(22.dp)
						.background(Color(0xE600A4DC), CircleShape),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_check),
						contentDescription = null,
						modifier = Modifier.size(12.dp),
						tint = Color.White,
					)
				}
			}
		}

		Spacer(modifier = Modifier.width(14.dp))

		Column(
			modifier = Modifier
				.weight(1f)
				.padding(vertical = 10.dp, horizontal = 0.dp)
				.padding(end = 14.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Text(
					text = "Episode ${episodeNumber ?: "?"}",
					fontSize = 13.sp,
					fontWeight = FontWeight.W600,
					color = Color.White.copy(alpha = 0.5f),
				)
				if (runtime != null) {
					Text(
						text = runtime,
						fontSize = 13.sp,
						color = Color.White.copy(alpha = 0.4f),
					)
				}
			}

			Spacer(modifier = Modifier.height(4.dp))

			Text(
				text = title,
				fontSize = 17.sp,
				fontWeight = FontWeight.W600,
				color = Color.White,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)

			if (overview != null) {
				Spacer(modifier = Modifier.height(4.dp))
				Text(
					text = overview,
					fontSize = 14.sp,
					color = Color.White.copy(alpha = 0.55f),
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
					lineHeight = 20.sp,
				)
			}
		}
	}
}

@Composable
fun SectionHeader(
	title: String,
	modifier: Modifier = Modifier,
) {
	Text(
		text = title,
		modifier = modifier.padding(bottom = 10.dp),
		fontSize = 20.sp,
		fontWeight = FontWeight.W600,
		color = Color.White,
	)
}

@Composable
fun PosterImage(
	imageUrl: String?,
	isLandscape: Boolean = false,
	isSquare: Boolean = false,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
			.then(
				when {
					isSquare -> Modifier
						.width(200.dp)
						.height(200.dp)
					isLandscape -> Modifier
						.width(280.dp)
						.height(158.dp)
					else -> Modifier
						.width(165.dp)
						.height(248.dp)
				}
			)
			.clip(RoundedCornerShape(8.dp))
			.background(Color.White.copy(alpha = 0.05f)),
		contentAlignment = Alignment.Center,
	) {
		if (imageUrl != null) {
			AsyncImage(
				model = imageUrl,
				contentDescription = null,
				modifier = Modifier.fillMaxSize(),
				contentScale = ContentScale.Crop,
			)
		} else {
			Icon(
				imageVector = ImageVector.vectorResource(R.drawable.ic_movie),
				contentDescription = null,
				modifier = Modifier.size(64.dp),
				tint = Color.White.copy(alpha = 0.15f),
			)
		}
	}
}

@Composable
fun DetailBackdrop(
	imageUrl: String?,
	modifier: Modifier = Modifier,
	blurAmount: Int = 0,
) {
	Box(modifier = modifier.fillMaxSize()) {
		if (imageUrl != null) {
			AsyncImage(
				model = imageUrl,
				contentDescription = null,
				modifier = Modifier
					.fillMaxSize()
					.then(if (blurAmount > 0) Modifier.blur(blurAmount.dp) else Modifier),
				contentScale = ContentScale.Crop,
				alpha = 0.8f,
			)
		}

		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.verticalGradient(
						colorStops = arrayOf(
							0.0f to Color.Transparent,
							0.3f to Color.Transparent,
							0.5f to Color(0x40101010),
							0.65f to Color(0xA0101010),
							0.8f to Color(0xE0101010),
							1.0f to Color(0xFF101010),
						),
					)
				)
		)
	}
}

@Composable
fun SimilarItemCard(
	title: String,
	imageUrl: String?,
	year: Int?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	onFocused: (() -> Unit)? = null,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	LaunchedEffect(isFocused) {
		if (isFocused) onFocused?.invoke()
	}

	Column(
		modifier = modifier
			.width(140.dp)
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(200.dp)
				.clip(RoundedCornerShape(6.dp))
				.then(
					if (isFocused) Modifier.border(2.dp, focusBorderColor(), RoundedCornerShape(6.dp))
					else Modifier.border(2.dp, Color.Transparent, RoundedCornerShape(6.dp))
				)
				.background(Color.White.copy(alpha = 0.05f)),
		) {
			if (imageUrl != null) {
				AsyncImage(
					model = imageUrl,
					contentDescription = title,
					modifier = Modifier.fillMaxSize(),
					contentScale = ContentScale.Crop,
				)
			}
		}

		Spacer(modifier = Modifier.height(6.dp))

		Text(
			text = title,
			fontSize = 13.sp,
			fontWeight = FontWeight.W500,
			color = Color.White.copy(alpha = 0.9f),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)

		if (year != null) {
			Text(
				text = year.toString(),
				fontSize = 11.sp,
				color = Color.White.copy(alpha = 0.5f),
			)
		}
	}
}

@Composable
fun LandscapeItemCard(
	title: String,
	imageUrl: String?,
	subtitle: String? = null,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	onFocused: (() -> Unit)? = null,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	LaunchedEffect(isFocused) {
		if (isFocused) onFocused?.invoke()
	}

	Column(
		modifier = modifier
			.width(220.dp)
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(124.dp)
				.clip(RoundedCornerShape(6.dp))
				.then(
					if (isFocused) Modifier.border(2.dp, focusBorderColor(), RoundedCornerShape(6.dp))
					else Modifier.border(2.dp, Color.Transparent, RoundedCornerShape(6.dp))
				)
				.background(Color.White.copy(alpha = 0.05f)),
		) {
			if (imageUrl != null) {
				AsyncImage(
					model = imageUrl,
					contentDescription = title,
					modifier = Modifier.fillMaxSize(),
					contentScale = ContentScale.Crop,
				)
			}
		}

		Spacer(modifier = Modifier.height(6.dp))

		Text(
			text = title,
			fontSize = 13.sp,
			fontWeight = FontWeight.W500,
			color = Color.White.copy(alpha = 0.9f),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)

		if (subtitle != null) {
			Text(
				text = subtitle,
				fontSize = 11.sp,
				color = Color.White.copy(alpha = 0.5f),
			)
		}
	}
}

/**
 * Track item card for music album/playlist track lists
 */
@Composable
fun TrackItemCard(
	trackNumber: Int,
	title: String,
	artist: String?,
	runtime: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	onFocused: (() -> Unit)? = null,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	LaunchedEffect(isFocused) {
		if (isFocused) onFocused?.invoke()
	}

	Row(
		modifier = modifier
			.fillMaxWidth()
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			)
			.focusable(interactionSource = interactionSource)
			.background(
				color = if (isFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent,
				shape = RoundedCornerShape(8.dp),
			)
			.border(
				width = if (isFocused) 2.dp else 0.dp,
				color = if (isFocused) Color.White else Color.Transparent,
				shape = RoundedCornerShape(8.dp),
			)
			.padding(horizontal = 16.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = trackNumber.toString(),
			fontSize = 16.sp,
			color = Color.White.copy(alpha = 0.6f),
			modifier = Modifier.width(40.dp),
		)

		Column(
			modifier = Modifier.weight(1f),
		) {
			Text(
				text = title,
				fontSize = 18.sp,
				fontWeight = FontWeight.W500,
				color = Color.White,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			if (artist != null) {
				Text(
					text = artist,
					fontSize = 14.sp,
					color = Color.White.copy(alpha = 0.6f),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}

		if (runtime != null) {
			Text(
				text = runtime,
				fontSize = 16.sp,
				color = Color.White.copy(alpha = 0.6f),
			)
		}
	}
}

/**
 * Modern track/version selector dialog styled to match the detail page refresh.
 * Renders a rounded, glass-morphism panel with focusable list items.
 */
@Composable
fun TrackSelectorDialog(
	title: String,
	options: List<String>,
	selectedIndex: Int,
	onSelect: (Int) -> Unit,
	onDismiss: () -> Unit,
) {
	val initialFocusRequester = remember { FocusRequester() }

	val selectorFocusColor = focusBorderColor()

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Box(
			modifier = Modifier
				.fillMaxSize(),
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
				Text(
					text = title,
					fontSize = 20.sp,
					fontWeight = FontWeight.W600,
					color = Color.White,
					modifier = Modifier
						.padding(horizontal = 24.dp)
						.padding(bottom = 12.dp),
				)

				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(1.dp)
						.background(Color.White.copy(alpha = 0.08f)),
				)

				Spacer(modifier = Modifier.height(8.dp))

				LazyColumn {
					itemsIndexed(options) { index, option ->
						val interactionSource = remember { MutableInteractionSource() }
						val isFocused by interactionSource.collectIsFocusedAsState()
						val isSelected = index == selectedIndex

						val focusModifier = if (index == selectedIndex.coerceIn(0, options.lastIndex)) {
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
								) { onSelect(index) }
								.focusable(interactionSource = interactionSource)
								.background(
									when {
										isFocused -> Color.White.copy(alpha = 0.12f)
										else -> Color.Transparent
									},
								)
								.padding(horizontal = 24.dp, vertical = 14.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							Box(
								modifier = Modifier
									.size(18.dp)
									.border(
										width = 2.dp,
										color = if (isSelected) focusBorderColor() else Color.White.copy(alpha = 0.3f),
										shape = CircleShape,
									),
								contentAlignment = Alignment.Center,
							) {
								if (isSelected) {
									Box(
										modifier = Modifier
											.size(10.dp)
											.background(focusBorderColor(), CircleShape),
									)
								}
							}

							Spacer(modifier = Modifier.width(16.dp))

							Text(
								text = option,
								fontSize = 16.sp,
								fontWeight = if (isSelected) FontWeight.W600 else FontWeight.W400,
								color = when {
									isSelected -> focusBorderColor()
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
			}
		}

		LaunchedEffect(Unit) {
			initialFocusRequester.requestFocus()
		}
	}
}
