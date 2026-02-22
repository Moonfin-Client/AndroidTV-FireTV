package org.jellyfin.androidtv.ui.browsing

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.SessionRepositoryState
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.service.UpdateCheckerService
import org.jellyfin.androidtv.data.syncplay.SyncPlayManager
import org.jellyfin.androidtv.databinding.ActivityMainBinding
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.InteractionTrackerViewModel
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.navigation.NavigationAction
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.androidtv.ui.playback.ThemeMusicPlayer
import org.jellyfin.androidtv.ui.screensaver.InAppScreensaver
import org.jellyfin.androidtv.ui.settings.compat.MainActivitySettings
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.util.applyTheme
import org.jellyfin.androidtv.util.isMediaSessionKeyEvent
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class MainActivity : FragmentActivity() {
	private val navigationRepository by inject<NavigationRepository>()
	private val sessionRepository by inject<SessionRepository>()
	private val userRepository by inject<UserRepository>()
	private val interactionTrackerViewModel by viewModel<InteractionTrackerViewModel>()
	private val workManager by inject<WorkManager>()
	private val updateCheckerService by inject<UpdateCheckerService>()
	private val userPreferences by inject<UserPreferences>()
	private val themeMusicPlayer by inject<ThemeMusicPlayer>()
	private val syncPlayManager by inject<SyncPlayManager>()
	private val playbackLauncher by inject<PlaybackLauncher>()

	private lateinit var binding: ActivityMainBinding
	private val showExitDialog = mutableStateOf(false)

	private val backPressedCallback = object : OnBackPressedCallback(false) {
		override fun handleOnBackPressed() {
			if (navigationRepository.canGoBack) {
				navigationRepository.goBack()
			} else {
				// User is on home screen, show exit confirmation
				showExitConfirmation()
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		applyTheme()

		super.onCreate(savedInstanceState)

		// Wait for session restoration before validating authentication
		// This prevents race condition where activity recreates before session is restored
		lifecycleScope.launch {
			sessionRepository.state
				.filter { it == SessionRepositoryState.READY }
				.first()

			if (!validateAuthentication()) return@launch
			
			setupSyncPlayQueueLauncher()
			setupActivity(savedInstanceState)
		}
	}

	private fun setupActivity(savedInstanceState: Bundle?) {
		interactionTrackerViewModel.keepScreenOn.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { keepScreenOn ->
				if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
				else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			}.launchIn(lifecycleScope)

		onBackPressedDispatcher.addCallback(this, backPressedCallback)
		if (savedInstanceState == null && navigationRepository.canGoBack) navigationRepository.reset(clearHistory = true)

		navigationRepository.currentAction
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { action ->
				handleNavigationAction(action)
				// Always enable back callback to handle exit confirmation
				backPressedCallback.isEnabled = true
				interactionTrackerViewModel.notifyInteraction(canCancel = false, userInitiated = false)
			}.launchIn(lifecycleScope)

		binding = ActivityMainBinding.inflate(layoutInflater)
		binding.background.setContent { AppBackground() }
		binding.settings.setContent { MainActivitySettings() }
		binding.screensaver.setContent { InAppScreensaver() }
		binding.exitDialog.setContent {
			if (showExitDialog.value) {
				ExitConfirmationDialog(
					onConfirm = { finish() },
					onDismiss = { showExitDialog.value = false },
				)
			}
		}
		setContentView(binding.root)

		// Check for updates on app launch (libre builds only)
		if (org.jellyfin.androidtv.BuildConfig.ENABLE_OTA_UPDATES) {
			checkForUpdatesOnLaunch()
		}
	}
	
	private fun setupSyncPlayQueueLauncher() {
		// Set up callback to handle queue loading when no active PlaybackController exists
		syncPlayManager.queueLaunchCallback = { itemIds, startIndex, startPositionTicks ->
			lifecycleScope.launch {
				val queueResult = org.jellyfin.androidtv.data.syncplay.SyncPlayQueueHelper.fetchQueue(
					itemIds = itemIds,
					startIndex = startIndex,
					startPositionTicks = startPositionTicks,
				)
				
				if (queueResult != null) {
					playbackLauncher.launch(
						context = this@MainActivity,
						items = queueResult.items,
						position = queueResult.startPositionMs.toInt(),
						itemsPosition = queueResult.startIndex
					)
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()

		// Skip auth check while session is still restoring — onCreate handles it once READY.
		// Prevents false bounce to StartupActivity when returning from external player after process death.
		if (sessionRepository.state.value != SessionRepositoryState.READY) return

		if (!validateAuthentication()) return

		applyTheme()

		interactionTrackerViewModel.activityPaused = false
	}

	private fun validateAuthentication(): Boolean {
		if (sessionRepository.currentSession.value == null || userRepository.currentUser.value == null) {
			Timber.w("Activity ${this::class.qualifiedName} started without a session, bouncing to StartupActivity")
			startActivity(Intent(this, StartupActivity::class.java))
			finish()
			return false
		}

		return true
	}

	private fun checkForUpdatesOnLaunch() {
		// Check if update notifications are enabled
		if (!userPreferences[UserPreferences.updateNotificationsEnabled]) {
			Timber.d("Update notifications are disabled")
			return
		}

		lifecycleScope.launch(Dispatchers.IO) {
			try {
				val result = updateCheckerService.checkForUpdate()
				result.onSuccess { updateInfo ->
					if (updateInfo != null && updateInfo.isNewer) {
						// Show toast on main thread
						launch(Dispatchers.Main) {
							Toast.makeText(
								this@MainActivity,
								"Update available: ${updateInfo.version}",
								Toast.LENGTH_LONG
							).show()
						}
						Timber.i("Update available: ${updateInfo.version}")
					} else {
						Timber.d("No updates available")
					}
				}.onFailure { error ->
					Timber.e(error, "Failed to check for updates")
				}
			} catch (e: Exception) {
				Timber.e(e, "Error checking for updates on launch")
			}
		}
	}

	override fun onPause() {
		super.onPause()

		interactionTrackerViewModel.activityPaused = true
	}

	override fun onStop() {
		super.onStop()

		// Stop theme music when app goes to background
		themeMusicPlayer.stop()

		workManager.enqueue(OneTimeWorkRequestBuilder<LeanbackChannelWorker>().build())

		// Only destroy session if app is finishing, not just temporarily stopping
		if (isFinishing) {
			lifecycleScope.launch(Dispatchers.IO) {
				Timber.i("MainActivity finishing - destroying session")
				sessionRepository.restoreSession(destroyOnly = true)
			}
		} else {
			Timber.d("MainActivity stopped (not finishing) - preserving session")
		}
	}

	private fun handleNavigationAction(action: NavigationAction) {

		when (action) {
			is NavigationAction.NavigateFragment -> binding.contentView.navigate(action)
			NavigationAction.GoBack -> binding.contentView.goBack()

			NavigationAction.Nothing -> Unit
		}
	}

	// Forward key events to fragments
	private fun Fragment.onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
		var result = childFragmentManager.fragments.any { it.onKeyEvent(keyCode, event) }
		if (!result && this is View.OnKeyListener) result = onKey(currentFocus, keyCode, event)
		return result
	}

	private fun onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
		// Ignore the key event that closes the screensaver
		if (interactionTrackerViewModel.visible.value) {
			interactionTrackerViewModel.notifyInteraction(canCancel = event?.action == KeyEvent.ACTION_UP, userInitiated = true)
			return true
		}

		return supportFragmentManager.fragments
			.any { it.onKeyEvent(keyCode, event) }
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyDown(keyCode, event)

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)

	override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)

	override fun onUserInteraction() {
		super.onUserInteraction()

		interactionTrackerViewModel.notifyInteraction(false, userInitiated = true)
	}

	@Suppress("RestrictedApi") // False positive
	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		// Ignore the key event that closes the screensaver
		if (!event.isMediaSessionKeyEvent() && interactionTrackerViewModel.visible.value) {
			interactionTrackerViewModel.notifyInteraction(canCancel = event.action == KeyEvent.ACTION_UP, userInitiated = true)
			return true
		}

		@Suppress("RestrictedApi") // False positive
		return super.dispatchKeyEvent(event)
	}

	@Suppress("RestrictedApi") // False positive
	override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean {
		// Ignore the key event that closes the screensaver
		if (!event.isMediaSessionKeyEvent() && interactionTrackerViewModel.visible.value) {
			interactionTrackerViewModel.notifyInteraction(canCancel = event.action == KeyEvent.ACTION_UP, userInitiated = true)
			return true
		}

		@Suppress("RestrictedApi") // False positive
		return super.dispatchKeyShortcutEvent(event)
	}

	override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
		// Ignore the touch event that closes the screensaver
		if (interactionTrackerViewModel.visible.value) {
			interactionTrackerViewModel.notifyInteraction(canCancel = true, userInitiated = true)
			return true
		}

		return super.dispatchTouchEvent(ev)
	}

	private fun showExitConfirmation() {
		if (!userPreferences[UserPreferences.confirmExit]) {
			finish()
			return
		}

		showExitDialog.value = true
	}

	override fun onDestroy() {
		showExitDialog.value = false
		super.onDestroy()
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)

		// Check if this is just an input device change (keyboard/navigation)
		// These changes don't require theme reapplication and can cause visual glitches during playback
		val isInputDeviceChange = newConfig.keyboard != resources.configuration.keyboard ||
			newConfig.keyboardHidden != resources.configuration.keyboardHidden ||
			newConfig.navigation != resources.configuration.navigation

		if (isInputDeviceChange) {
			Timber.d("Input device configuration changed - preserving activity and playback state without theme reapplication")
			// Don't call applyTheme() for input device changes to avoid visual glitches during playback
		} else {
			Timber.d("Configuration changed - applying theme")
			applyTheme()
		}
	}
}
