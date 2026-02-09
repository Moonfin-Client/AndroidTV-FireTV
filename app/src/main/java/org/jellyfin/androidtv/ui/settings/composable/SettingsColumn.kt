package org.jellyfin.androidtv.ui.settings.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import org.jellyfin.design.Tokens

@Composable
fun SettingsColumn(content: LazyListScope.() -> Unit) {
	val listState = rememberSaveable(saver = LazyListState.Saver) {
		LazyListState()
	}
	val focusState = rememberSaveable(saver = SettingsFocusState.Saver) {
		SettingsFocusState()
	}

	LazyColumn(
		state = listState,
		modifier = Modifier.padding(Tokens.Space.spaceSm),
		verticalArrangement = Arrangement.spacedBy(Tokens.Space.spaceXs),
	) {
		FocusTrackingScope(this, focusState).apply(content)
	}
}

/**
 * Tracks which item was last focused so it can be restored after back-navigation.
 * Uses plain vars (not Compose State) to avoid recomposition races during focus restoration.
 */
private class SettingsFocusState(
	var lastFocusedIndex: Int = -1,
	var needsRestore: Boolean = false,
) {
	companion object {
		val Saver = Saver<SettingsFocusState, Int>(
			save = { it.lastFocusedIndex },
			restore = { SettingsFocusState(lastFocusedIndex = it, needsRestore = it >= 0) },
		)
	}
}

/**
 * Wraps [LazyListScope.item] to add focus tracking and restoration.
 * Records which item index is focused, and on restore requests focus on the saved item.
 */
private class FocusTrackingScope(
	private val delegate: LazyListScope,
	private val focusState: SettingsFocusState,
) : LazyListScope by delegate {
	private var itemIndex = 0

	override fun item(
		key: Any?,
		contentType: Any?,
		content: @Composable LazyItemScope.() -> Unit,
	) {
		val index = itemIndex++
		val state = focusState
		delegate.item(key, contentType) {
			val lazyItemScope = this
			val focusRequester = remember { FocusRequester() }
			Box(
				modifier = Modifier
					.focusRequester(focusRequester)
					.onFocusChanged { focusChanged ->
						if (focusChanged.hasFocus) {
							state.lastFocusedIndex = index
							state.needsRestore = false
						}
					},
			) {
				lazyItemScope.content()
			}
			if (state.needsRestore && index == state.lastFocusedIndex) {
				LaunchedEffect(Unit) {
					try {
						focusRequester.requestFocus()
					} catch (_: IllegalStateException) {
						// FocusRequester not yet attached to a composable
					}
				}
			}
		}
	}
}
