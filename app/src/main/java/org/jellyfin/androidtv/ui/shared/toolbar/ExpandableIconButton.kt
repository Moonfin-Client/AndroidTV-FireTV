package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonColors

/**
 * An icon button that expands to show a text label when focused
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandableIconButton(
	icon: ImageVector,
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	onLongClick: (() -> Unit)? = null,
	colors: ButtonColors,
	contentDescription: String? = label,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()
	val bringIntoViewRequester = remember { BringIntoViewRequester() }
	val scope = rememberCoroutineScope()
	
	val scale by animateFloatAsState(
		targetValue = if (isFocused) 1.05f else 1f,
		animationSpec = tween(durationMillis = 200),
		label = "ButtonScale"
	)
	
	// Animate padding to make buttons more compact when not focused
	val horizontalPadding by animateDpAsState(
		targetValue = if (isFocused) 12.dp else 6.dp,
		animationSpec = tween(durationMillis = 200),
		label = "ButtonPadding"
	)
	val verticalPadding by animateDpAsState(
		targetValue = if (isFocused) 8.dp else 6.dp,
		animationSpec = tween(durationMillis = 200),
		label = "ButtonVerticalPadding"
	)
	
	// Bring button into view when focused
	LaunchedEffect(isFocused) {
		if (isFocused) {
			scope.launch {
				bringIntoViewRequester.bringIntoView()
			}
		}
	}

	Button(
		onClick = onClick,
		onLongClick = onLongClick,
		colors = colors,
		modifier = modifier
			.bringIntoViewRequester(bringIntoViewRequester)
			.scale(scale),
		interactionSource = interactionSource,
		contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding),
	) {
		Row(
			horizontalArrangement = Arrangement.Center,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(
				imageVector = icon,
				contentDescription = contentDescription,
			)
			
			// Text label that appears when focused
			if (isFocused) {
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
