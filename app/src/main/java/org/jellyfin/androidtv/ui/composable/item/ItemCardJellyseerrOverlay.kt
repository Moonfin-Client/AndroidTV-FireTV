package org.jellyfin.androidtv.ui.composable.item

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text

/**
 * Compose overlay for Jellyseerr media cards, replacing the View-based PosterBadges.
 * Renders media type badge (top-left) and availability indicator (top-right).
 */
@Composable
@Stable
fun ItemCardJellyseerrOverlay(
	item: JellyseerrDiscoverItemDto,
) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.padding(6.dp)
	) {
		MediaTypeBadge(
			mediaType = item.mediaType,
			modifier = Modifier.align(Alignment.TopStart),
		)

		AvailabilityIndicator(
			status = item.mediaInfo?.status,
			modifier = Modifier.align(Alignment.TopEnd),
		)
	}
}

@Composable
@Stable
private fun MediaTypeBadge(
	mediaType: String?,
	modifier: Modifier = Modifier,
) {
	if (mediaType == null) return

	val (text, bgColor) = when (mediaType) {
		"movie" -> "MOVIE" to Color(0xFF3B82F6) // blue-500
		"tv" -> "SERIES" to Color(0xFF8B5CF6) // purple-500
		else -> return
	}

	Text(
		text = text,
		fontSize = 10.sp,
		color = Color.White,
		letterSpacing = 0.8.sp,
		modifier = modifier
			.background(bgColor.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
			.padding(horizontal = 6.dp, vertical = 2.dp),
	)
}

@Composable
@Stable
private fun AvailabilityIndicator(
	status: Int?,
	modifier: Modifier = Modifier,
) {
	if (status == null) return

	val iconRes = when (status) {
		5 -> R.drawable.ic_available
		4 -> R.drawable.ic_partially_available
		3 -> R.drawable.ic_indigo_spinner
		else -> return
	}

	if (status == 3) {
		val infiniteTransition = rememberInfiniteTransition(label = "spinner")
		val rotation by infiniteTransition.animateFloat(
			initialValue = 0f,
			targetValue = 360f,
			animationSpec = infiniteRepeatable(
				animation = tween(durationMillis = 1000, easing = LinearEasing),
				repeatMode = RepeatMode.Restart,
			),
			label = "spinnerRotation",
		)
		Icon(
			imageVector = ImageVector.vectorResource(iconRes),
			contentDescription = null,
			tint = Color.White,
			modifier = modifier
				.size(20.dp)
				.rotate(rotation),
		)
	} else {
		Icon(
			imageVector = ImageVector.vectorResource(iconRes),
			contentDescription = null,
			tint = Color.Unspecified,
			modifier = modifier.size(20.dp),
		)
	}
}
