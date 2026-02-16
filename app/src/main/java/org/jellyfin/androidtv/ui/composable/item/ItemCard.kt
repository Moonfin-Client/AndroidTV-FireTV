package org.jellyfin.androidtv.ui.composable.item

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.focusBorderColor

@Composable
@Stable
fun ItemCard(
	modifier: Modifier = Modifier,
	focused: Boolean = false,
	image: @Composable BoxScope.() -> Unit,
	overlay: (@Composable BoxScope.() -> Unit)? = null,
	shape: Shape = JellyfinTheme.shapes.medium,
) {
	val borderColor = focusBorderColor()
	Box(
		modifier = modifier
			.then(if (focused) Modifier.border(2.dp, borderColor, shape) else Modifier)
			.clip(shape)
			.background(JellyfinTheme.colorScheme.surface, shape)
	) {
		image()

		if (overlay != null) {
			Box(
				modifier = Modifier.fillMaxSize(),
				content = overlay
			)
		}
	}
}
