package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.modifier.overscan
import org.jellyfin.androidtv.ui.composable.rememberCurrentTime

@Composable
fun Logo(modifier: Modifier = Modifier) {
	Image(
		painter = painterResource(R.drawable.app_logo),
		contentDescription = stringResource(R.string.app_name),
		modifier = modifier,
	)
}

@Composable
fun Toolbar(
	modifier: Modifier = Modifier,
	start: @Composable () -> Unit = { Logo() },
	center: @Composable () -> Unit = {},
	end: @Composable () -> Unit = { ToolbarClock() },
) {
	ToolbarLayout(
		modifier = modifier
			.height(95.dp)
			.overscan(),
		start = start,
		center = center,
		end = end,
	)
}

@Composable
fun ToolbarClock(
	modifier: Modifier = Modifier,
) {
	val currentTime by rememberCurrentTime()
	Text(
		text = currentTime,
		fontSize = 20.sp,
		color = Color.White,
		modifier = modifier,
	)
}

@Composable
fun ToolbarLayout(
	modifier: Modifier = Modifier,
	start: @Composable () -> Unit = {},
	center: @Composable () -> Unit = {},
	end: @Composable () -> Unit = {},
) = SubcomposeLayout(modifier = modifier) { constraints ->
	val sideConstraints = constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth / 3, minHeight = 0)
	val startPlaceables = subcompose("start", content = start).map { it.measure(sideConstraints) }
	val endPlaceables = subcompose("end", content = end).map { it.measure(sideConstraints) }

	val sideWidth = maxOf(
		startPlaceables.maxOfOrNull { it.width } ?: 0,
		endPlaceables.maxOfOrNull { it.width } ?: 0,
	)

	val centerWidth = (constraints.maxWidth - 2 * sideWidth).coerceAtLeast(0)
	val centerPlaceables = subcompose("center", content = center)
		.map { it.measure(constraints.copy(minWidth = 0, maxWidth = centerWidth)) }

	val height = listOf(
		startPlaceables.maxOfOrNull { it.height } ?: 0,
		centerPlaceables.maxOfOrNull { it.height } ?: 0,
		endPlaceables.maxOfOrNull { it.height } ?: 0
	).maxOrNull() ?: 0

	layout(constraints.maxWidth, height) {
		startPlaceables.forEach { it.place(0, (height - it.height) / 2) }
		centerPlaceables.forEach { it.place((constraints.maxWidth - it.width) / 2, (height - it.height) / 2) }
		endPlaceables.forEach { it.place(constraints.maxWidth - it.width, (height - it.height) / 2) }
	}
}

@Composable
fun ToolbarButtons(
	modifier: Modifier = Modifier,
	content: @Composable RowScope.() -> Unit,
) {
	val scrollState = rememberScrollState()
	val scope = rememberCoroutineScope()
	val pillShape = RoundedCornerShape(28.dp)

	Row(
		modifier = modifier
			.background(
				brush = Brush.verticalGradient(
					colors = listOf(
						Color.Gray.copy(alpha = 0.5f),
						Color.Gray.copy(alpha = 0.5f)
					)
				),
				shape = pillShape
			)
			.clip(pillShape)
			.padding(horizontal = 8.dp, vertical = 0.dp)
			.horizontalScroll(scrollState)
			.focusRestorer()
			.focusGroup()
			.onFocusChanged { focusState ->
				if (focusState.hasFocus) {
					scope.launch {
						scrollState.scrollBy(0f)
					}
				}
			},
		horizontalArrangement = Arrangement.spacedBy(0.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		JellyfinTheme(
			colorScheme = JellyfinTheme.colorScheme.copy(
				button = Color.Transparent
			)
		) {
			content()
		}
	}
}
