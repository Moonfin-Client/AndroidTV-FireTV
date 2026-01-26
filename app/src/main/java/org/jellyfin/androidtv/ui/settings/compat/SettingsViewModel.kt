package org.jellyfin.androidtv.ui.settings.compat

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.androidtv.ui.playback.ThemeMusicPlayer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SettingsViewModel : ViewModel(), KoinComponent {
	private val themeMusicPlayer by inject<ThemeMusicPlayer>()
	
	private val _visible = MutableStateFlow(false)
	val visible get() = _visible.asStateFlow()

	// Counter that increments each time settings are closed, used to trigger preference reloads
	private val _settingsClosedCounter = MutableStateFlow(0)
	val settingsClosedCounter get() = _settingsClosedCounter.asStateFlow()

	fun show() {
		themeMusicPlayer.stop()
		_visible.value = true
	}

	fun hide() {
		_visible.value = false
		_settingsClosedCounter.value++
	}
}
