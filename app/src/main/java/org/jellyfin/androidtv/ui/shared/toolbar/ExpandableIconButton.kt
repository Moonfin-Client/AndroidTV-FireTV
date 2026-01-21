package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonColors

/**
 * An icon button that expands to show a text label when focused
 */
@Composable
fun ExpandableIconButton(
	icon: ImageVector,
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	colors: ButtonColors,
	contentDescription: String? = label,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()
	val scale by animateFloatAsState(
		targetValue = if (isFocused) 1.05f else 1f,
		animationSpec = tween(durationMillis = 200),
		label = "ButtonScale"
	)

	Button(
		onClick = onClick,
		colors = colors,
		modifier = modifier.scale(scale),
		interactionSource = interactionSource,
	) {
		Row(
			horizontalArrangement = Arrangement.Center,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(
				imageVector = icon,
				contentDescription = contentDescription,
			)
			
			// Animated text label that appears when focused
			AnimatedVisibility(
				visible = isFocused,
				enter = expandHorizontally(
					expandFrom = Alignment.Start,
					animationSpec = tween(durationMillis = 250)
				) + fadeIn(animationSpec = tween(durationMillis = 250)),
				exit = shrinkHorizontally(
					shrinkTowards = Alignment.Start,
					animationSpec = tween(durationMillis = 200)
				) + fadeOut(animationSpec = tween(durationMillis = 200)),
			) {
				Row {
					Spacer(modifier = Modifier.width(8.dp))
					ProvideTextStyle(
						JellyfinTheme.typography.default.copy(fontWeight = FontWeight.Bold)
					) {
						Text(
							text = label,
							modifier = Modifier.padding(end = 4.dp)
						)
					}
				}
			}
		}
	}
}
