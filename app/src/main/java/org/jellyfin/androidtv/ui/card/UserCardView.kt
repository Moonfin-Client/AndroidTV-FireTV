package org.jellyfin.androidtv.ui.card

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.ProfilePicture
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.focusBorderColor
import org.jellyfin.androidtv.util.MenuBuilder
import org.jellyfin.androidtv.util.popupMenu
import org.jellyfin.androidtv.util.showIfNotEmpty

@Composable
fun UserCard(
	image: @Composable () -> Unit,
	name: @Composable () -> Unit,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
	shape: Shape = RoundedCornerShape(12.dp),
) {
	val focused by interactionSource.collectIsFocusedAsState()

	val focusColor = focusBorderColor()
	val borderColor = when {
		focused -> focusColor
		else -> JellyfinTheme.colorScheme.button
	}
	val textColor = when {
		focused -> JellyfinTheme.colorScheme.onInputFocused
		else -> JellyfinTheme.colorScheme.onInput
	}
	val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "UserCardFocusScale")

	Column(
		modifier = modifier
			.scale(scale)
			.aspectRatio(1f)
			.clip(shape)
			.background(JellyfinTheme.colorScheme.button.copy(alpha = 0.35f), shape)
			.border(2.dp, borderColor, shape)
			.focusable(interactionSource = interactionSource)
			.clickable(interactionSource = interactionSource, onClick = onClick, indication = null),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f),
			contentAlignment = Alignment.Center,
		) {
			image()
		}

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 8.dp, vertical = 6.dp)
				.basicMarquee(
					iterations = if (focused) Int.MAX_VALUE else 0,
					initialDelayMillis = 0,
				),
			contentAlignment = Alignment.Center,
		) {
			ProvideTextStyle(
				LocalTextStyle.current.copy(
					color = textColor,
				)
			) {
				name()
			}
		}
	}
}

class UserCardView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {
	var name by mutableStateOf<String?>(null)
	var image by mutableStateOf<String?>(null)
	private var focused by mutableStateOf(false)

	init {
		isFocusable = true
		descendantFocusability = FOCUS_BLOCK_DESCENDANTS
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false
	}

	fun setPopupMenu(init: MenuBuilder.() -> Unit) {
		setOnLongClickListener {
			popupMenu(context, this, init = init).showIfNotEmpty()
		}
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
		if (super.onKeyUp(keyCode, event)) return true

		// Menu key should show the popup menu
		if (event.keyCode == KeyEvent.KEYCODE_MENU) return performLongClick()

		return false
	}

	override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
		super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)

		focused = gainFocus
	}

	@Composable
	override fun Content() {
		val interactionSource = remember { MutableInteractionSource() }

		// Forward focus events to the interaction source
		val focusInteraction = remember { FocusInteraction.Focus() }
		LaunchedEffect(focused) {
			if (focused) interactionSource.emit(focusInteraction)
			else interactionSource.emit(FocusInteraction.Unfocus(focusInteraction))
		}

		UserCard(
			image = {
				ProfilePicture(
					url = image,
					iconPadding = PaddingValues(24.dp),
					modifier = Modifier.fillMaxSize()
				)
			},
			name = {
				Text(
					text = name.orEmpty(),
					maxLines = 1
				)
			},
			modifier = Modifier
				.padding(horizontal = 6.dp, vertical = 8.dp)
				.width(110.dp),
			interactionSource = interactionSource,
			// We use our own click handler for views used in presenters
			onClick = {}
		)
	}
}
